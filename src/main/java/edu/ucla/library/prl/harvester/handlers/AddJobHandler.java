
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.InvalidJobJsonException;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.OaipmhUtils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for adding jobs.
 */
public final class AddJobHandler extends AbstractRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     */
    public AddJobHandler(final Vertx aVertx) {
        super(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final Job job = new Job(aContext.body().asJsonObject());
            final Future<Void> validateOaipmhIdentifiers = OaipmhUtils.validateIdentifiers(myVertx,
                    job.getRepositoryBaseURL(), job.getSets().orElse(List.of()));

            validateOaipmhIdentifiers.onSuccess(nil -> {
                myHarvestScheduleStoreService.addJob(job).compose(jobID -> {
                    return myHarvestJobSchedulerService.addJob(jobID, job).map(jobID);
                }).onSuccess(jobID -> {
                    final JsonObject responseBody = Job.withID(job, jobID).toJson();

                    response.setStatusCode(HttpStatus.SC_CREATED)
                            .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                            .end(responseBody.encode());
                }).onFailure(aContext::fail);
            }).onFailure(details -> {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
            });
        } catch (final InvalidJobJsonException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }
}
