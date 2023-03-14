
package edu.ucla.library.prl.harvester.handlers;

import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Param;

import info.freelibrary.util.StringUtils;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

/**
 * A handler for removing institutions.
 */
public final class RemoveInstitutionHandler extends AbstractSolrAwareWriteOperationHandler {

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public RemoveInstitutionHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx, aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));

            // Before modifying the database, get institution data so that we can update Solr accordingly
            myHarvestScheduleStoreService.getInstitution(id).compose(institution -> {
                // Get the list of Jobs that we need to remove
                final Future<Void> removeAssociatedJobs = myHarvestScheduleStoreService.listJobs().compose(jobs -> {
                    @SuppressWarnings({ "rawtypes", "unchecked" })
                    final Stream<Future> associatedJobRemovals = jobs.parallelStream() //
                            .filter(job -> job.getInstitutionID() == id) //
                            .map(job -> {
                                final int jobID = job.getID().get();

                                return myHarvestScheduleStoreService.removeJob(jobID).compose(nil -> {
                                    return (Future) myHarvestJobSchedulerService.removeJob(jobID);
                                });
                            });

                    return CompositeFuture.all(associatedJobRemovals.toList()).mapEmpty();
                });

                // Remove the Jobs, then remove the Institution
                return removeAssociatedJobs.compose(nil -> {
                    return myHarvestScheduleStoreService.removeInstitution(id);
                }).compose(nil -> {
                    return updateSolr(Tuple.of(institution.toJson()));
                });
            }).onSuccess(solrResponse -> {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT).end();
            }).onFailure(aContext::fail);
        } catch (final NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }

    /**
     * Removes the institution doc, and all item record docs that were harvested by jobs associated with the
     * institution.
     *
     * @param aData A Tuple of the institution as JSON
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple aData) {
        final Institution institution = new Institution(aData.getJsonObject(0));

        final String itemRecordDocsQuery = StringUtils.format("institutionName:\"{}\"", institution.getName());
        final String institutionDocQuery =
                StringUtils.format("id:\"{}\"", institution.toSolrDoc().getFieldValue(Institution.ID));
        final String query = StringUtils.format("{} OR {}", itemRecordDocsQuery, institutionDocQuery);
        final CompletionStage<UpdateResponse> removal =
                mySolrClient.deleteByQuery(query).thenCompose(result -> mySolrClient.commit());

        return Future.fromCompletionStage(removal);
    }
}
