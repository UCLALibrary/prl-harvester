
package edu.ucla.library.prl.harvester.services;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import org.dspace.xoai.model.oaipmh.Record;
import org.dspace.xoai.model.oaipmh.Set;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.JobResult;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.OaipmhUtils;

import info.freelibrary.util.IllegalArgumentI18nException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceException;

/**
 * The implementation of {@link HarvestService}.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class HarvestServiceImpl implements HarvestService {

    /**
     * A logger for the service.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestServiceImpl.class, MessageCodes.BUNDLE);

    /**
     * A Vert.x instance.
     */
    private final Vertx myVertx;

    /**
     * The HTTP timeout to use with the internal OAI-PMH client.
     */
    private final int myOaipmhClientHttpTimeout;

    /**
     * The User-Agent HTTP request header to use for outgoing requests.
     */
    private final String myHarvesterUserAgent;

    /**
     * An HTTP client for verifying thumbnail image URLs.
     */
    private final WebClient myWebClient;

    /**
     * A client for sending transformed metadata records to Solr.
     */
    private final JavaAsyncSolrClient mySolrClient;

    /**
     * A proxy to the harvest schedule store service, for retrieving institution names.
     */
    private final HarvestScheduleStoreService myHarvestScheduleStoreService;

    /**
     * Creates an instance of the service.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    protected HarvestServiceImpl(final Vertx aVertx, final JsonObject aConfig) {
        final String userAgent = Config.getHarvesterUserAgent(aConfig);

        myVertx = aVertx;
        myHarvesterUserAgent = userAgent;
        myOaipmhClientHttpTimeout = Config.getOaipmhClientHttpTimeout(aConfig);
        myWebClient = WebClient.create(aVertx, new WebClientOptions().setUserAgent(userAgent));
        mySolrClient = JavaAsyncSolrClient.create(aConfig.getString(Config.SOLR_CORE_URL));
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
    }

    @Override
    public Future<JobResult> run(final Job aJob) {
        final URL baseURL = aJob.getRepositoryBaseURL();
        final int institutionID = aJob.getInstitutionID();
        final Future<List<Set>> listSets =
                OaipmhUtils.listSets(myVertx, baseURL, myOaipmhClientHttpTimeout, myHarvesterUserAgent);
        final Future<Institution> getInstitution = myHarvestScheduleStoreService.getInstitution(institutionID);

        if (aJob.getID().isEmpty()) {
            return Future
                    .failedFuture(new IllegalArgumentI18nException(MessageCodes.BUNDLE, MessageCodes.PRL_002, Job.ID));
        }

        return CompositeFuture.all(listSets, getInstitution).compose(results -> {
            final List<Set> sets = results.resultAt(0);
            final Institution institution = results.resultAt(1);
            final Map<String, String> setNameLookup =
                    sets.stream().collect(Collectors.toMap(Set::getSpec, Set::getName));
            final String institutionName = institution.getName();

            final List<String> targetSets;
            final OffsetDateTime startTime;
            final Future<Stream<Record>> harvest;

            if (aJob.getSets().isPresent() && !aJob.getSets().get().isEmpty()) {
                // Harvest only the specified sets
                targetSets = aJob.getSets().get();
            } else {
                // Harvest all sets in the repository
                targetSets = new LinkedList<>(setNameLookup.keySet());
            }

            startTime = OffsetDateTime.now();

            LOGGER.debug(MessageCodes.PRL_008, aJob.toJson(), startTime);

            // TODO: de-duplicate list of records (based on identifier; some sets may contain the same record)
            harvest = OaipmhUtils.listRecords(myVertx, baseURL, targetSets, aJob.getMetadataPrefix(),
                    aJob.getLastSuccessfulRun(), myOaipmhClientHttpTimeout, myHarvesterUserAgent);

            return harvest.compose(records -> {
                // Map each OAI-PMH record to a future Solr document
                final Stream<Future<SolrInputDocument>> docFutures = records.map(record -> {
                    return HarvestServiceUtils.getSolrDocument(record, institutionName, baseURL, setNameLookup,
                            myWebClient);
                });

                return reifySolrDocs(docFutures);
            }).compose(docs -> {
                final Future<Void> solrResult;

                if (!docs.isEmpty()) {
                    solrResult = updateSolr(docs).mapEmpty();
                } else {
                    solrResult = Future.succeededFuture();
                }

                return solrResult.map(nil -> new JobResult(aJob.getID().get(), startTime, docs.size()));
            });
        }).recover(details -> {
            // TODO: consider retrying on failure
            return Future.failedFuture(new ServiceException(hashCode(), details.toString()));
        });
    }

    /**
     * @param aRecords A stream of Futures that resolve to Solr documents
     * @return A Future that resolves to a list of the Solr documents
     */
    private Future<List<SolrInputDocument>> reifySolrDocs(final Stream<Future<SolrInputDocument>> aRecords) {
        final Promise<List<SolrInputDocument>> promise = Promise.promise();

        myVertx.<List<SolrInputDocument>>executeBlocking(execution -> {
            // Collecting the stream may require making network calls (utilizing any resumption tokens returned with the
            // ListRecords response, or checking if a URL references a thumbnail image), possibly many; hence the use of
            // a worker thread
            CompositeFuture.all(aRecords.collect(Collectors.toList())).map(CompositeFuture::<SolrInputDocument>list)
                    .onSuccess(execution::complete).onFailure(execution::fail);
        }, false, promise);

        return promise.future();
    }

    /**
     * Performs a Solr update.
     *
     * @param aDocs A list of Solr documents to add
     * @return The result of performing the Solr update
     */
    private Future<UpdateResponse> updateSolr(final List<SolrInputDocument> aDocs) {
        final CompletionStage<UpdateResponse> addition =
                mySolrClient.addDocs(aDocs).thenCompose(result -> mySolrClient.commit());

        return Future.fromCompletionStage(addition);
    }

    @Override
    public Future<Void> close() {
        myWebClient.close();
        mySolrClient.shutdown();

        return myHarvestScheduleStoreService.close();
    }
}
