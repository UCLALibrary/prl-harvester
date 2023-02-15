
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MediaType;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for listing jobs.
 */
public final class ListJobsHandler extends AbstractRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     */
    public ListJobsHandler(final Vertx aVertx) {
        super(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        myHarvestScheduleStoreService.listJobs().onSuccess(jobs -> {
            final JsonArray responseBody = new JsonArray(jobs.stream().map(Job::toJson).toList());

            aContext.response().setStatusCode(HttpStatus.SC_OK)
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                    .end(responseBody.encode());
        }).onFailure(aContext::fail);
    }
}
