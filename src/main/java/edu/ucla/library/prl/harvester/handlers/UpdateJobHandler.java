
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.InvalidJobJsonException;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.Param;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for updating jobs.
 */
public final class UpdateJobHandler extends AbstractRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     */
    public UpdateJobHandler(final Vertx aVertx) {
        super(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));
            final Job job = new Job(aContext.body().asJsonObject());

            myHarvestScheduleStoreService.updateJob(id, job).compose(nil -> {
                return myHarvestJobSchedulerService.updateJob(id, job);
            }).onSuccess(nil -> {
                final JsonObject responseBody = Job.withID(job, id).toJson();

                response.setStatusCode(HttpStatus.SC_OK)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .end(responseBody.encode());
            }).onFailure(aContext::fail);
        } catch (final InvalidJobJsonException | NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }
}
