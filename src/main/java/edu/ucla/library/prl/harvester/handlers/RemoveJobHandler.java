
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.Param;

import info.freelibrary.util.StringUtils;

import io.vavr.Tuple2;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for removing jobs.
 */
public final class RemoveJobHandler extends AbstractSolrAwareWriteOperationHandler<Tuple2<Job, Institution>> {

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public RemoveJobHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx, aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));

            getJobAndInstitution(id).compose(jobAndInst -> {
                // Update the database, in-memory scheduler, and Solr
                final Future<Void> removal = myHarvestScheduleStoreService.removeJob(id).compose(nil -> {
                    return myHarvestJobSchedulerService.removeJob(id);
                }).compose(nil -> {
                    return updateSolr(jobAndInst).mapEmpty();
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
     * Removes all item records that were harvested by the job.
     *
     * @param aData A 2-tuple of the job to remove and its associated institution
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple2<Job, Institution> aData) {
        final Job job = aData._1();
        final Institution institution = aData._2();
        final Optional<String> recordRemovalQuery = getRecordRemovalQuery(institution.getName(), job.getSets());

        if (recordRemovalQuery.isPresent()) {
            final String solrQuery = recordRemovalQuery.get();
            final CompletionStage<UpdateResponse> removal =
                    mySolrClient.deleteByQuery(solrQuery).thenCompose(result -> mySolrClient.commit());

            return Future.fromCompletionStage(removal);
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * @param anInstitutionName An institution name
     * @param aSets A list of sets
     * @return A Solr query that can be used to remove records from the given sets associated with the given institution
     */
    static Optional<String> getRecordRemovalQuery(final String anInstitutionName, final List<String> aSets) {

        if (!aSets.isEmpty()) {
            final String[] collectionQueryClauses = aSets.stream() //
                    .map(set -> StringUtils.format("set_spec:\"{}\"", set)) //
                    .toArray(length -> new String[aSets.size()]);
            final String query = StringUtils.format("institutionName:\"{}\" AND ({})", anInstitutionName,
                    String.join(" OR ", collectionQueryClauses));

            return Optional.of(query);
        } else {
            return Optional.empty();
        }
    }
}
