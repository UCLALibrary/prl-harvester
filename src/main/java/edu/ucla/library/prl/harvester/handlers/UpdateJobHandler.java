
package edu.ucla.library.prl.harvester.handlers;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.InvalidJobJsonException;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.OaipmhUtils;
import edu.ucla.library.prl.harvester.Param;

import io.vavr.Tuple;
import io.vavr.Tuple3;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for updating jobs.
 */
public final class UpdateJobHandler extends AbstractSolrAwareWriteOperationHandler<Tuple3<Job, Job, Institution>> {

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public UpdateJobHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx, aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));
            final Job job = new Job(aContext.body().asJsonObject());
            final URL baseURL = job.getRepositoryBaseURL();
            final List<String> sets = job.getSets();

            OaipmhUtils.validateIdentifiers(myVertx, baseURL, sets, myOaipmhClientHttpTimeout, myHarvesterUserAgent)
                    .onSuccess(none -> {
                        getJobAndInstitution(id).compose(oldJobAndInstitution -> {
                            // Update the database, the in-memory scheduler, and Solr
                            final Job oldJob = oldJobAndInstitution._1();
                            final Institution institution = oldJobAndInstitution._2();
                            final Job jobToSubmit;
                            if (hasNewSets(oldJob, job)) {
                                jobToSubmit = new Job(job.getInstitutionID(), job.getRepositoryBaseURL(), job.getSets(),
                                        job.getScheduleCronExpression(), null);
                            } else {
                                jobToSubmit = job;
                            }
                            final Future<Void> update =
                                    myHarvestScheduleStoreService.updateJob(id, jobToSubmit).compose(nil -> {
                                        return myHarvestJobSchedulerService.updateJob(id, jobToSubmit);
                                    }).compose(nil -> {
                                        return updateSolr(Tuple.of(oldJob, jobToSubmit, institution)).mapEmpty();
                                    });

                            return update;
                        }).onSuccess(nil -> {
                            final JsonObject responseBody = Job.withID(job, id).toJson();

                            response.setStatusCode(HttpStatus.SC_OK)
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                                    .end(responseBody.encode());
                        }).onFailure(aContext::fail);
                    }).onFailure(details -> {
                        response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
                    });
        } catch (final InvalidJobJsonException | NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }

    /**
     * Removes all item records that belong to the sets removed from the job (if any).
     *
     * @param aData A 3-tuple of the old job, the new job, and the associated institution
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple3<Job, Job, Institution> aData) {
        final Job oldJob = aData._1();
        final Job newJob = aData._2();
        final Institution institution = aData._3();

        final List<String> oldJobSets = oldJob.getSets();
        final List<String> newJobSets = newJob.getSets();
        final Future<List<String>> getActualOldJobSets;

        if (!oldJobSets.isEmpty() && !newJobSets.isEmpty()) {
            // Still selective harvesting, althought the list of sets may have changed, so determine that
            getActualOldJobSets = Future.succeededFuture(oldJobSets);
        } else if (oldJobSets.isEmpty() && !newJobSets.isEmpty()) {
            // From non-selective harvesting to selective, so it's very likely that there are sets to remove
            // Must query OAI-PMH repository in order to get the sets belonging to the old job
            getActualOldJobSets = OaipmhUtils
                    .listSets(myVertx, oldJob.getRepositoryBaseURL(), myOaipmhClientHttpTimeout, myHarvesterUserAgent)
                    .map(OaipmhUtils::getSetSpecs);
            // TODO: make it impossible to change the base URL
        } else if (!oldJobSets.isEmpty() && newJobSets.isEmpty()) {
            // From selective harvesting to non-selective, so nothing to remove
            return Future.succeededFuture();
        } else {
            // Still harvesting entire repository, so nothing to remove
            return Future.succeededFuture();
        }

        // If we determined that there are sets to remove, do that now
        return getActualOldJobSets.compose(sets -> {
            final List<String> setsToRemove = getDifference(sets, newJobSets);
            final Optional<String> recordRemovalQuery =
                    RemoveJobHandler.getRecordRemovalQuery(institution.getName(), setsToRemove);

            if (recordRemovalQuery.isPresent()) {
                final String solrQuery = recordRemovalQuery.get();
                final CompletionStage<UpdateResponse> removal =
                        mySolrClient.deleteByQuery(solrQuery).thenCompose(result -> mySolrClient.commit());

                return Future.fromCompletionStage(removal);
            } else {
                return Future.succeededFuture();
            }
        });
    }

    /**
     * @param anOldJob A Job representing an original state
     * @param aNewJob A Job representing a new, updated state
     * @return True if new job has sets not contained in old job
     */
    private boolean hasNewSets(final Job anOldJob, final Job aNewJob) {
        return getDifference(aNewJob.getSets(),
                anOldJob.getSets())
                        .isEmpty() ||
                !anOldJob.getSets().isEmpty() && aNewJob.getSets().isEmpty() &&
                        (getDifference(OaipmhUtils.listSets(myVertx, anOldJob.getRepositoryBaseURL(),
                                myOaipmhClientHttpTimeout, myHarvesterUserAgent).map(OaipmhUtils::getSetSpecs).result(),
                                anOldJob.getSets()).isEmpty());
    }

    /**
     * @param anOldList A list representing an original state
     * @param aNewList A list representing a new, updated state
     * @return The list of elements in {@code anOldList} that are not in {@code aNewList}
     */
    private static List<String> getDifference(final List<String> anOldList, final List<String> aNewList) {
        return anOldList.stream().filter(setSpec -> !aNewList.contains(setSpec)).toList();
    }
}
