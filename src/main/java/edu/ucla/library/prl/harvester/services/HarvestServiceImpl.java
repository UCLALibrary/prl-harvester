
package edu.ucla.library.prl.harvester.services;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;

import org.dspace.xoai.model.oaipmh.Record;
import org.dspace.xoai.model.oaipmh.Set;
import org.dspace.xoai.serviceprovider.exceptions.BadArgumentException;
import org.dspace.xoai.serviceprovider.exceptions.NoSetHierarchyException;
import org.dspace.xoai.serviceprovider.parameters.ListRecordsParameters;

import com.google.common.collect.ImmutableList;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.JobResult;
import edu.ucla.library.prl.harvester.MessageCodes;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
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
        myVertx = aVertx;
        myWebClient = WebClient.create(aVertx);
        mySolrClient = JavaAsyncSolrClient.create(aConfig.getString(Config.SOLR_CORE_URL));
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
    }

    @Override
    public Future<JobResult> run(final Job aJob) {
        final URL baseURL = aJob.getRepositoryBaseURL();

        return listSetsAsync(baseURL).compose(sets -> {
            final Map<String, String> setNameLookup =
                    sets.stream().collect(Collectors.toMap(Set::getSpec, Set::getName));
            final int institutionID = aJob.getInstitutionID();

            return myHarvestScheduleStoreService.getInstitution(institutionID).compose(institution -> {
                final String institutionName = institution.getName();

                final List<String> targetSets;
                final OffsetDateTime startTime;
                final Future<List<Record>> harvest;

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
                harvest = listRecords(baseURL, targetSets, aJob.getMetadataPrefix(), aJob.getLastSuccessfulRun());

                return harvest.compose(records -> {
                    final Future<Void> solrResult;

                    if (!records.isEmpty()) {
                        solrResult = updateSolr(records, institutionName, baseURL, setNameLookup).mapEmpty();
                    } else {
                        solrResult = Future.succeededFuture();
                    }

                    return solrResult.map(unused -> new JobResult(startTime, records.size()));
                });
            });
        }).recover(details -> {
            // TODO: consider retrying on failure
            return Future.failedFuture(new ServiceException(hashCode(), details.toString()));
        });
    }

    /**
     * Performs a listRecords operation.
     *
     * @param aBaseURL The OAI-PMH base URL
     * @param aSets The non-empty list of sets to harvest
     * @param aMetadataPrefix The OAI-PMH metadata prefix
     * @param aFrom The optional timestamp of the last successful run
     * @return The list of OAI-PMH records
     */
    private Future<List<Record>> listRecords(final URL aBaseURL, final List<String> aSets, final String aMetadataPrefix,
            final Optional<OffsetDateTime> aFrom) {
        @SuppressWarnings("rawtypes")
        final List<Future> selectiveHarvests = new LinkedList<>();

        for (final String setSpec : aSets) {
            final ListRecordsParameters params =
                    ListRecordsParameters.request().withMetadataPrefix(aMetadataPrefix).withSetSpec(setSpec);

            aFrom.ifPresent(from -> params.withFrom(Date.from(from.toInstant())));

            selectiveHarvests.add(listRecordsAsync(aBaseURL, params));
        }

        return CompositeFuture.all(selectiveHarvests).map(result -> {
            final List<ImmutableList<Record>> results = result.<ImmutableList<Record>>list();

            // Flatten the list of lists
            return results.parallelStream().flatMap(List::parallelStream).collect(Collectors.toUnmodifiableList());
        });
    }

    /**
     * Performs a Solr update.
     *
     * @param aRecords A list of OAI-PMH records
     * @param anInstitutionName The name of the institution
     * @param aBaseURL The OAI-PMH base URL
     * @param aSetNameLookup A lookup table that maps setSpec to setName
     * @return The result of performing the Solr update
     */
    private Future<UpdateResponse> updateSolr(final List<Record> aRecords, final String anInstitutionName,
            final URL aBaseURL, final Map<String, String> aSetNameLookup) {
        @SuppressWarnings("rawtypes")
        final List<Future> docResults = new LinkedList<>();

        // Transform each OAI-PMH XML record into a Solr document
        for (final Record record : aRecords) {
            final Future<SolrInputDocument> docResult = HarvestServiceUtils.getSolrDocument(record, anInstitutionName,
                    aBaseURL, aSetNameLookup, myWebClient);

            docResults.add(docResult);
        }

        return CompositeFuture.all(docResults).compose(result -> {
            final CompletionStage<UpdateResponse> updateResponse = mySolrClient //
                    .addDocs(result.<SolrInputDocument>list()) //
                    .thenCompose(res -> mySolrClient.commit());

            return Future.fromCompletionStage(updateResponse);
        });
    }

    /**
     * Provides an asynchronous API for the synchronous XOAI listSets API.
     *
     * @param aBaseURL An OAI-PMH base URL
     * @return A Future that resolves to a list of OAI-PMH sets
     */
    private Future<ImmutableList<Set>> listSetsAsync(final URL aBaseURL) {
        final Promise<ImmutableList<Set>> promise = Promise.promise();

        myVertx.<ImmutableList<Set>>executeBlocking(execution -> {
            try {
                final Iterator<Set> synchronousResult = HarvestServiceUtils.getNewOaipmhClient(aBaseURL).listSets();

                execution.complete(ImmutableList.copyOf(synchronousResult));
            } catch (final NoSetHierarchyException details) {
                execution.fail(details.getCause());
            }
        }, false, execution -> {
            if (execution.succeeded()) {
                promise.complete(execution.result());
            } else {
                promise.fail(execution.cause());
            }
        });

        return promise.future();
    }

    /**
     * Provides an asynchronous API for the synchronous XOAI listRecords API.
     *
     * @param aBaseURL An OAI-PMH base URL
     * @param aParams The OAI-PMH request parameters
     * @return A Future that resolves to a list of OAI-PMH records
     */
    private Future<ImmutableList<Record>> listRecordsAsync(final URL aBaseURL, final ListRecordsParameters aParams) {
        final Promise<ImmutableList<Record>> promise = Promise.promise();

        myVertx.<ImmutableList<Record>>executeBlocking(execution -> {
            try {
                final Iterator<Record> synchronousResult =
                        HarvestServiceUtils.getNewOaipmhClient(aBaseURL).listRecords(aParams);

                execution.complete(ImmutableList.copyOf(synchronousResult));
            } catch (final BadArgumentException details) {
                execution.fail(details.getCause());
            }
        }, false, execution -> {
            if (execution.succeeded()) {
                promise.complete(execution.result());
            } else {
                promise.fail(execution.cause());
            }
        });

        return promise.future();
    }

    @Override
    public Future<Void> close() {
        myWebClient.close();
        mySolrClient.shutdown();

        return myHarvestScheduleStoreService.close();
    }
}
