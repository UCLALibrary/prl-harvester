
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.InvalidJobJsonException;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.OaipmhUtils;
import edu.ucla.library.prl.harvester.Param;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

/**
 * A handler for updating jobs.
 */
public final class UpdateJobHandler extends AbstractSolrAwareWriteOperationHandler {

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
            final JsonObject jobJSON = aContext.body().asJsonObject();
            final Job job = new Job(jobJSON);
            final Future<Void> validateOaipmhIdentifiers = OaipmhUtils.validateIdentifiers(myVertx,
                    job.getRepositoryBaseURL(), job.getSets().orElse(List.of()));

            validateOaipmhIdentifiers.onSuccess(none -> {
                myHarvestScheduleStoreService.getJob(id).compose(oldJob -> {
                    return myHarvestScheduleStoreService.getInstitution(oldJob.getInstitutionID())
                            .compose(institution -> {
                                return myHarvestScheduleStoreService.updateJob(id, job).compose(nil -> {
                                    return myHarvestJobSchedulerService.updateJob(id, job);
                                }).compose(nil -> {
                                    return updateSolr(Tuple.of(oldJob.toJson(), jobJSON, institution.getName()));
                                });
                            });
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
     * @param aData A Tuple of the old JSON representation of the job, its new JSON representation, and the institution
     *        name
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple aData) {
        final Job oldJob = new Job(aData.getJsonObject(0));
        final Job newJob = new Job(aData.getJsonObject(1));
        final String institutionName = aData.getString(2);

        final Optional<List<String>> oldJobSets = oldJob.getSets();
        final Optional<List<String>> newJobSets = newJob.getSets();
        final Future<List<String>> getActualOldJobSets;

        if (oldJobSets.isPresent() && newJobSets.isPresent()) {
            // Still selective harvesting, althought the list of sets may have changed, so determine that
            getActualOldJobSets = Future.succeededFuture(oldJobSets.get());
        } else if (oldJobSets.isEmpty() && newJobSets.isPresent()) {
            // From non-selective harvesting to selective, so it's very likely that there are sets to remove
            // Must query OAI-PMH repository in order to get the sets belonging to the old job
            getActualOldJobSets =
                    OaipmhUtils.listSets(myVertx, oldJob.getRepositoryBaseURL()).map(OaipmhUtils::getSetSpecs);
            // TODO: make it impossible to change the base URL
        } else if (oldJobSets.isPresent() && newJobSets.isEmpty()) {
            // From selective harvesting to non-selective, so nothing to remove
            return Future.succeededFuture();
        } else {
            // Still harvesting entire repository, so nothing to remove
            return Future.succeededFuture();
        }

        // If we determined that there are sets to remove, do that now
        return getActualOldJobSets.compose(sets -> {
            final Optional<List<String>> setsToRemove = Optional.of(getDifference(sets, newJobSets.get()));
            final Future<String> getSolrQuery = RemoveJobHandler.getRecordRemovalQuery(myVertx, institutionName,
                    oldJob.getRepositoryBaseURL(), setsToRemove);

            return getSolrQuery.compose(solrQuery -> {
                final CompletionStage<UpdateResponse> removal =
                        mySolrClient.deleteByQuery(solrQuery).thenCompose(result -> mySolrClient.commit());

                return Future.fromCompletionStage(removal);
            });
        });
    }

    /**
     * @param anOldList A list representing an original state
     * @param aNewList A list representing a new, updated state
     * @return The list of elements that are in {@code aNewList} but not in {@code anOldList}
     */
    private static List<String> getDifference(final List<String> anOldList, final List<String> aNewList) {
        return anOldList.stream().filter(setSpec -> !aNewList.contains(setSpec)).toList();
    }
}
