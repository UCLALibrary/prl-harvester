
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.Param;

import info.freelibrary.util.StringUtils;

import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for removing institutions.
 */
public final class RemoveInstitutionHandler extends AbstractSolrAwareWriteOperationHandler<Tuple1<Institution>> {

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
            getInstitutionAndJobs(id).compose(institutionAndJobs -> {
                final Institution institution = institutionAndJobs._1();
                final List<Job> jobs = institutionAndJobs._2();

                @SuppressWarnings({ "rawtypes", "unchecked" })
                final List<Future> jobRemovals = jobs.parallelStream().map(job -> {
                    final int jobID = job.getID().get();

                    return myHarvestScheduleStoreService.removeJob(jobID).compose(nil -> {
                        return (Future) myHarvestJobSchedulerService.removeJob(jobID);
                    });
                }).toList();

                // If and when all the jobs are removed, remove the institution
                @SuppressWarnings("PMD.UnnecessaryLocalBeforeReturn")
                final Future<Void> removal = CompositeFuture.all(jobRemovals).compose(nil -> {
                    return myHarvestScheduleStoreService.removeInstitution(id).compose(none -> {
                        return updateSolr(Tuple.of(institution)).mapEmpty();
                    });
                });

                return removal;
            }).onSuccess(nil -> {
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
     * @param aData A 1-tuple of the institution
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple1<Institution> aData) {
        final Institution institution = aData._1();

        final String itemRecordDocsQuery = StringUtils.format("institutionName:\"{}\"", institution.getName());
        final String institutionDocQuery =
                StringUtils.format("id:\"{}\"", institution.toSolrDoc().getFieldValue(Institution.ID));
        final String query = StringUtils.format("{} OR {}", itemRecordDocsQuery, institutionDocQuery);
        final CompletionStage<UpdateResponse> removal =
                mySolrClient.deleteByQuery(query).thenCompose(result -> mySolrClient.commit());

        return Future.fromCompletionStage(removal);
    }

    /**
     * @param anInstitutionID An institution ID
     * @return A 2-tuple of the institution and the list of jobs associated with it
     */
    private Future<Tuple2<Institution, List<Job>>> getInstitutionAndJobs(final int anInstitutionID) {
        return myHarvestScheduleStoreService.getInstitution(anInstitutionID).compose(institution -> {
            // Get the list of Jobs that we need to remove
            return myHarvestScheduleStoreService.listJobs().map(jobs -> {
                // Filter out the jobs not associated with this institution
                return jobs.stream().filter(job -> job.getInstitutionID() == anInstitutionID).toList();
            }).map(filteredJobs -> Tuple.of(institution, filteredJobs));
        });
    }
}
