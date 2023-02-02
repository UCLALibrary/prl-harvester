
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for listing jobs.
 */
public final class ListJobsHandler implements Handler<RoutingContext> {

    /**
     * A proxy to the harvest schedule store service.
     */
    private final HarvestScheduleStoreService myHarvestScheduleStoreService;

    /**
     * @param aVertx A Vert.x instance
     */
    public ListJobsHandler(final Vertx aVertx) {
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        myHarvestScheduleStoreService.listJobs().onSuccess(jobs -> {
            final JsonArray responseBody = new JsonArray(jobs.stream().map(Job::toJson).toList());

            response.setStatusCode(HttpStatus.SC_OK)
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                    .end(responseBody.encode());
        }).onFailure(details -> {
            details.printStackTrace();

            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end(details.getMessage());
        });
    }
}
