
package edu.ucla.library.prl.harvester.services;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

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
     * The max batch size for Solr update queries.
     */
    private final int myMaxBatchSize;

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
        myMaxBatchSize = Config.getSolrUpdateMaxBatchSize(aConfig);
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
    }

    @Override
    public Future<JobResult> run(final Job aJob) {
        final URL baseURL = aJob.getRepositoryBaseURL();
        final int institutionID = aJob.getInstitutionID();
        final Future<List<Set>> listSets =
                OaipmhUtils.listSets(myVertx, baseURL, myOaipmhClientHttpTimeout, myHarvesterUserAgent);
        final Future<Institution> getInstitution = myHarvestScheduleStoreService.getInstitution(institutionID);
        final int jobID;

        if (aJob.getID().isEmpty()) {
            return Future
                    .failedFuture(new IllegalArgumentI18nException(MessageCodes.BUNDLE, MessageCodes.PRL_002, Job.ID));
        }

        jobID = aJob.getID().get();

        return CompositeFuture.all(listSets, getInstitution).compose(results -> {
            final List<Set> sets = results.resultAt(0);
            final Institution institution = results.resultAt(1);
            final Map<String, String> setNameLookup =
                    sets.stream().collect(Collectors.toMap(Set::getSpec, Set::getName));
            final String institutionName = institution.getName();

            final List<String> targetSets;
            final OffsetDateTime startTime;
            final Future<Iterator<Record>> harvest;

            if (!aJob.getSets().isEmpty()) {
                // Harvest only the specified sets
                targetSets = aJob.getSets();
            } else {
                // Harvest all sets in the repository
                targetSets = new LinkedList<>(setNameLookup.keySet());
            }

            startTime = OffsetDateTime.now();

            LOGGER.debug(MessageCodes.PRL_008, aJob.toJson());

            // TODO: de-duplicate list of records (based on identifier; some sets may contain the same record)
            harvest = OaipmhUtils.listRecords(myVertx, baseURL, targetSets, aJob.getMetadataPrefix(),
                    aJob.getLastSuccessfulRun(), myOaipmhClientHttpTimeout, myHarvesterUserAgent);

            return harvest.compose(records -> {
                final Promise<Tuple2<Integer, Integer>> promise = Promise.promise();

                myVertx.executeBlocking(execution -> {
                    updateSolrInBatches(records, institutionName, baseURL, setNameLookup, myMaxBatchSize)
                            .onSuccess(execution::complete).onFailure(execution::fail);
                }, false, promise);

                return promise.future();
            }).map(docAndDeletedRecordCounts -> {
                final int docCount = docAndDeletedRecordCounts._1();
                final int deletedRecordCount = docAndDeletedRecordCounts._2();
                final JobResult result = new JobResult(jobID, startTime, docCount, deletedRecordCount);

                LOGGER.debug(MessageCodes.PRL_049, jobID, result.toJson());

                return result;
            });
        }).recover(details -> {
            final String errorMsg = details.getMessage();

            LOGGER.error(MessageCodes.PRL_050, jobID, errorMsg);

            // TODO: consider retrying on failure
            return Future.failedFuture(new ServiceException(hashCode(), errorMsg));
        });
    }

    /**
     * Performs Solr update queries while consuming the stream of OAI-PMH records in batches.
     * <p>
     * This is a potentially long-running function, so it should be run on a worker thread.
     *
     * @param aRecords A set of records
     * @param anInstitutionName The name of the associated institution
     * @param aBaseURL An OAI-PMH repository base URL
     * @param aSetNameLookup A lookup table that maps setSpec to setName
     * @param aMaxBatchSize The maximum number of records to handle per Solr query
     * @return A Future that resolves to a 2-tuple containing: the number of Solr documents added or updated, and the
     *         number of Solr documents deleted
     */
    private Future<Tuple2<Integer, Integer>> updateSolrInBatches(final Iterator<Record> aRecords,
            final String anInstitutionName, final URL aBaseURL, final Map<String, String> aSetNameLookup,
            final int aMaxBatchSize) {
        @SuppressWarnings("rawtypes")
        final List<Future> recordMappingsBatch = new ArrayList<>(aMaxBatchSize);
        final List<String> deletedRecordIdsBatch = new ArrayList<>(aMaxBatchSize);

        final int newRecordCount;
        final int deletedRecordCount;

        int runningNewRecordCount = 0;
        int runningDeletedRecordCount = 0;

        try {
            while (aRecords.hasNext()) {
                final Record record = aRecords.next();
                final Header header = record.getHeader();

                if (!header.isDeleted()) {
                    recordMappingsBatch.add(HarvestServiceUtils.getSolrDocument(record, anInstitutionName, aBaseURL,
                            aSetNameLookup, myWebClient));

                    if (recordMappingsBatch.size() == aMaxBatchSize) {
                        addDocs(HarvestServiceUtils.unwrapAll(recordMappingsBatch));
                        runningNewRecordCount += recordMappingsBatch.size();
                        recordMappingsBatch.clear();
                    }
                } else {
                    deletedRecordIdsBatch.add(header.getIdentifier());

                    if (deletedRecordIdsBatch.size() == aMaxBatchSize) {
                        deleteByIds(deletedRecordIdsBatch);
                        runningDeletedRecordCount += deletedRecordIdsBatch.size();
                        deletedRecordIdsBatch.clear();
                    }
                }
            }

            // Handle the final batches (if any)

            if (!recordMappingsBatch.isEmpty()) {
                addDocs(HarvestServiceUtils.unwrapAll(recordMappingsBatch));
                runningNewRecordCount += recordMappingsBatch.size();
                recordMappingsBatch.clear();
            }

            if (!deletedRecordIdsBatch.isEmpty()) {
                deleteByIds(deletedRecordIdsBatch);
                runningDeletedRecordCount += deletedRecordIdsBatch.size();
                deletedRecordIdsBatch.clear();
            }
        } catch (final CompletionException details) {
            // Issuing a rollback is potentially problematic in the event that another harvest job is in progress (since
            // Solr doesn't support simultaneous transactions), but the likelihood of such an error occuring seems slim
            return Future.fromCompletionStage(mySolrClient.rollback())
                    .compose(result -> Future.failedFuture(details.getCause()));
        } catch (final InterruptedException details) {
            return Future.fromCompletionStage(mySolrClient.rollback()).compose(result -> Future.failedFuture(details));
        }

        // Get some final variables for the lambda below

        newRecordCount = runningNewRecordCount;
        deletedRecordCount = runningDeletedRecordCount;

        return Future.fromCompletionStage(mySolrClient.commit())
                .map(response -> Tuple.of(newRecordCount, deletedRecordCount));
    }

    /**
     * Synchronous wrapper around {@link JavaAsyncSolrClient#addDocs(java.util.Collection)}.
     *
     * @param aDocs A list of Solr documents to add (possibly empty)
     * @return The result of performing the Solr update
     * @throws InterruptedException If the calling thread is interrupted
     */
    private UpdateResponse addDocs(final List<SolrInputDocument> aDocs) throws InterruptedException {
        final CompletionStage<UpdateResponse> add = mySolrClient.addDocs(aDocs);
        final CountDownLatch addQueryCompletion = new CountDownLatch(1);

        add.thenRun(addQueryCompletion::countDown);
        addQueryCompletion.await();

        return add.toCompletableFuture().join();
    }

    /**
     * Synchronous wrapper around {@link JavaAsyncSolrClient#deleteByIds(List)}.
     *
     * @param aDeletedRecordIDs A list of record identifiers that have been deleted (possibly empty)
     * @return The result of performing the Solr update
     * @throws InterruptedException If the calling thread is interrupted
     */
    private UpdateResponse deleteByIds(final List<String> aDeletedRecordIDs) throws InterruptedException {
        final CompletionStage<UpdateResponse> delete = mySolrClient.deleteByIds(aDeletedRecordIDs);
        final CountDownLatch deleteQueryCompletion = new CountDownLatch(1);

        delete.thenRun(deleteQueryCompletion::countDown);
        deleteQueryCompletion.await();

        return delete.toCompletableFuture().join();
    }

    @Override
    public Future<Void> close() {
        myWebClient.close();
        mySolrClient.shutdown();

        return myHarvestScheduleStoreService.close();
    }
}
