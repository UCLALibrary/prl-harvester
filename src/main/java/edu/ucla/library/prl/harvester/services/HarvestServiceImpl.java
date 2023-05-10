
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
import io.vavr.control.Either;

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
                // Map each OAI-PMH record to either:
                // - a future Solr document; or
                // - its identifier, if it has been deleted from the repository
                final Stream<Either<String, Future<SolrInputDocument>>> recordMappings = records.map(record -> {
                    final Header header = record.getHeader();

                    if (header.isDeleted()) {
                        return Either.left(header.getIdentifier());
                    } else {
                        return Either.right(HarvestServiceUtils.getSolrDocument(record, institutionName, baseURL,
                                setNameLookup, myWebClient));
                    }
                });

                return reifyRecordMappings(recordMappings);
            }).compose(docsAndDeletedRecordIDs -> {
                // Phew, now we have some concrete things to tell Solr about!
                final List<SolrInputDocument> docs = docsAndDeletedRecordIDs._1();
                final List<String> deletedRecordIDs = docsAndDeletedRecordIDs._2();

                return updateSolr(docs, deletedRecordIDs)
                        .map(result -> new JobResult(aJob.getID().get(), startTime, docs.size()));
            });
        }).recover(details -> {
            // TODO: consider retrying on failure
            return Future.failedFuture(new ServiceException(hashCode(), details.toString()));
        });
    }

    /**
     * @param aMixedBag A stream whose elements are either: (a) Futures that resolve to Solr documents, or (b)
     *        identifiers of deleted records
     * @return A Future that resolves to a 2-tuple containing: a list of the Solr documents (possibly empty), and a list
     *         of identifiers of deleted records (also possibly empty)
     */
    private Future<Tuple2<List<SolrInputDocument>, List<String>>>
            reifyRecordMappings(final Stream<Either<String, Future<SolrInputDocument>>> aMixedBag) {

        // This promise will be returned and (eventually) completed
        final Promise<Tuple2<List<SolrInputDocument>, List<String>>> promise = Promise.promise();

        myVertx.executeBlocking(execution -> {
            // Collecting the stream may require making network calls (utilizing any resumption tokens returned with the
            // ListRecords response, or checking if a URL references a thumbnail image), possibly many; hence the use of
            // a worker thread (XOAI's network I/O is blocking)

            // First, split the stream into two parts: existing records ("true") and deleted records ("false")
            final Map<Boolean, List<Either<String, Future<SolrInputDocument>>>> partitionedRecords =
                    aMixedBag.collect(Collectors.partitioningBy(Either::isRight));

            // Get Solr docs for all of the existing records (the "true" bucket of the partition)
            final CompositeFuture solrDocReification = CompositeFuture.all( //
                    partitionedRecords.get(true).parallelStream().map(Either::get).collect(Collectors.toList()));

            // Collect the identifiers of all the deleted records (the "false" bucket of the partition)
            final List<String> deletedRecordIDs =
                    partitionedRecords.get(false).parallelStream().map(Either::getLeft).toList();

            // Combine results from each bucket and return it
            solrDocReification.map(CompositeFuture::<SolrInputDocument>list).map(docs -> {
                return Tuple.of(docs, deletedRecordIDs);
            }).onSuccess(execution::complete).onFailure(execution::fail);
        }, false, promise);

        return promise.future();
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
