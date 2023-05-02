
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.InvalidJobJsonException;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.OaipmhUtils;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for adding jobs.
 */
public final class AddJobsHandler extends AbstractRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public AddJobsHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx, aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();
        final Stream<Future<Job>> semanticValidations =
                aContext.body().asJsonArray().stream().map(this::validateInstitution);

        CompositeFuture.all(semanticValidations.collect(Collectors.toList())).onSuccess(result -> {
            final List<Job> jobs = result.list();

            myHarvestScheduleStoreService.addJobs(jobs).compose(jobsWithIDs -> {
                return myHarvestJobSchedulerService.addJobs(jobsWithIDs).map(jobsWithIDs);
            }).onSuccess(jobsWithIDs -> {
                final JsonArray responseBody = new JsonArray(jobsWithIDs.stream().map(Job::toJson).toList());

                response.setStatusCode(HttpStatus.SC_CREATED)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .end(responseBody.encode());
            }).onFailure(aContext::fail);
        }).onFailure(details -> {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        });
    }

    /**
     * @param aJob An entry in the request body array
     * @return A Future that succeeds if the entry is a valid {@link Job}
     */
    private Future<Job> validateInstitution(final Object aJob) {
        Future<Job> deserializationResult;

        try {
            deserializationResult = Future.succeededFuture(new Job(JsonObject.mapFrom(aJob)));
        } catch (final IllegalArgumentException | InvalidJobJsonException details) {
            deserializationResult = Future.failedFuture(details);
        }

        return deserializationResult.compose(job -> {
            final Future<Void> oaipmhIdentifierValidationResult = OaipmhUtils.validateIdentifiers(myVertx,
                    job.getRepositoryBaseURL(), job.getSets().orElse(List.of()), myHarvesterUserAgent);

            return oaipmhIdentifierValidationResult.map(job);
        });
    }
}
