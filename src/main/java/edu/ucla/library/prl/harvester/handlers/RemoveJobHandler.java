
package edu.ucla.library.prl.harvester.handlers;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.OaipmhUtils;
import edu.ucla.library.prl.harvester.Param;

import info.freelibrary.util.StringUtils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

/**
 * A handler for removing jobs.
 */
public final class RemoveJobHandler extends AbstractSolrAwareWriteOperationHandler {

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

            // Query database first to get job info for removing it
            myHarvestScheduleStoreService.getJob(id).compose(job -> {
                return myHarvestScheduleStoreService.getInstitution(job.getInstitutionID()).compose(institution -> {
                    // Do all the solr updating in this context
                    return myHarvestScheduleStoreService.removeJob(id).compose(nil -> {
                        return myHarvestJobSchedulerService.removeJob(id);
                    }).compose(nil -> {
                        return updateSolr(Tuple.of(job.toJson(), institution.toJson()));
                    });
                });
            }).onSuccess(solrResponse -> {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT).end();
            }).onFailure(aContext::fail);
        } catch (final NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }

    /**
     * Removes all item records that were harvested by the job.
     *
     * @param aData A 2-tuple of the ID of the job to remove, and its JSON representation
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple aData) {
        final Job job = new Job(aData.getJsonObject(0));
        final Institution institution = new Institution(aData.getJsonObject(1));
        final Future<String> futureRecordRemovalQuery =
                getRecordRemovalQuery(myVertx, institution.getName(), job.getRepositoryBaseURL(), job.getSets());

        return futureRecordRemovalQuery.compose(solrQuery -> {
            final CompletionStage<UpdateResponse> removal =
                    mySolrClient.deleteByQuery(solrQuery).thenCompose(result -> mySolrClient.commit());

            return Future.fromCompletionStage(removal);
        });
    }

    /**
     * @param aVertx A Vert.x instance
     * @param anInstitutionName An institution name
     * @param aBaseURL An OAI-PMH repository base URL
     * @param aSets A list of sets
     * @return A Solr query that can be used to remove records from the given sets associated with the given institution
     */
    static Future<String> getRecordRemovalQuery(final Vertx aVertx, final String anInstitutionName, final URL aBaseURL,
            final Optional<List<String>> aSets) {
        final Future<List<String>> getSetsToRemove;

        if (aSets.isPresent() && !aSets.get().isEmpty()) {
            getSetsToRemove = Future.succeededFuture(aSets.get());
        } else {
            // Missing or empty list means all sets in the repository
            getSetsToRemove = OaipmhUtils.listSets(aVertx, aBaseURL).map(OaipmhUtils::getSetSpecs);
        }

        return getSetsToRemove.map(sets -> {
            final String[] collectionQueryClauses = sets.stream() //
                    .map(set -> StringUtils.format("set_spec:\"{}\"", set)) //
                    .toArray(x -> new String[sets.size()]);

            return StringUtils.format("institutionName:\"{}\" AND ({})", anInstitutionName,
                    String.join(" OR ", collectionQueryClauses));
        });
    }
}
