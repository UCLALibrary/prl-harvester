
package edu.ucla.library.prl.harvester.services;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import org.dspace.xoai.model.oaipmh.Header;
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

import io.vavr.Tuple;
import io.vavr.Tuple2;

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
                final Promise<Tuple2<List<SolrInputDocument>, List<String>>> promise = Promise.promise();

                myVertx.executeBlocking(execution -> {
                    partitionAndMapRecords(records, institutionName, baseURL, setNameLookup)
                            .onSuccess(execution::complete).onFailure(execution::fail);
                }, false, promise);

                return promise.future();
            }).compose(docsAndDeletedRecordIDs -> {
                final List<SolrInputDocument> docs = docsAndDeletedRecordIDs._1();
                final List<String> deletedRecordIDs = docsAndDeletedRecordIDs._2();

                return updateSolr(docs, deletedRecordIDs).map(
                        result -> new JobResult(aJob.getID().get(), startTime, docs.size(), deletedRecordIDs.size()));
            });
        }).recover(details -> {
            // TODO: consider retrying on failure
            return Future.failedFuture(new ServiceException(hashCode(), details.toString()));
        });
    }

    /**
     * Partitions the records into those that have been deleted and those that haven't, and then maps deleted ones to
     * their identifier and not-deleted ones to a Solr document.
     * <p>
     * This is a potentially long-running function that performs blocking network I/O, so should be run on a worker
     * thread.
     *
     * @param aRecords A set of records
     * @param anInstitutionName The name of the associated institution
     * @param aBaseURL An OAI-PMH repository base URL
     * @param aSetNameLookup A lookup table that maps setSpec to setName
     * @return A Future that resolves to a 2-tuple containing: a list of the Solr documents (possibly empty), and a list
     *         of identifiers of deleted records (also possibly empty)
     */
    private Future<Tuple2<List<SolrInputDocument>, List<String>>> partitionAndMapRecords(final Stream<Record> aRecords,
            final String anInstitutionName, final URL aBaseURL, final Map<String, String> aSetNameLookup) {
        @SuppressWarnings("rawtypes")
        final List<Future> recordMappings = new LinkedList<>();
        final List<String> deletedRecordIDs = new LinkedList<>();
        final Iterator<Record> it = aRecords.iterator();

        while (it.hasNext()) {
            final Record record = it.next();
            final Header header = record.getHeader();

            if (header.isDeleted()) {
                deletedRecordIDs.add(header.getIdentifier());
            } else {
                recordMappings.add(HarvestServiceUtils.getSolrDocument(record, anInstitutionName, aBaseURL,
                        aSetNameLookup, myWebClient));
            }
        }

        return CompositeFuture.all(recordMappings).map(CompositeFuture::<SolrInputDocument>list).map(docs -> {
            return Tuple.of(docs, deletedRecordIDs);
        });
    }

    /**
     * Performs a Solr update.
     *
     * @param aDocs A list of Solr documents to add (possibly empty)
     * @param aDeletedRecordIDs A list of record identifiers that have been deleted (possibly empty)
     * @return The result of performing the Solr update (if any was necessary)
     */
    private Future<UpdateResponse> updateSolr(final List<SolrInputDocument> aDocs,
            final List<String> aDeletedRecordIDs) {
        final CompletionStage<UpdateResponse> update;

        if (!aDocs.isEmpty() && !aDeletedRecordIDs.isEmpty()) {
            // Both docs to add and delete
            update = mySolrClient.addDocs(aDocs).thenCompose(result -> mySolrClient.deleteByIds(aDeletedRecordIDs));
        } else if (!aDocs.isEmpty() && aDeletedRecordIDs.isEmpty()) {
            // Only docs to add
            update = mySolrClient.addDocs(aDocs);
        } else if (aDocs.isEmpty() && !aDeletedRecordIDs.isEmpty()) {
            // Only docs to delete
            update = mySolrClient.deleteByIds(aDeletedRecordIDs);
        } else {
            // Nothing to do
            return Future.succeededFuture();
        }

        return Future.fromCompletionStage(update.thenCompose(result -> mySolrClient.commit()));
    }

    @Override
    public Future<Void> close() {
        myWebClient.close();
        mySolrClient.shutdown();

        return myHarvestScheduleStoreService.close();
    }
}
