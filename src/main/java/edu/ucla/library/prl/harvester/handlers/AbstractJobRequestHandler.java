
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.services.HarvestJobSchedulerService;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService.HarvestScheduleStoreServiceException;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * An abstract base class for request handlers that deal with {@link Job}s.
 */
public abstract class AbstractJobRequestHandler implements Handler<RoutingContext> {

    /**
     * A proxy to the harvest schedule store service.
     */
    protected final HarvestScheduleStoreService myHarvestScheduleStoreService;

    /**
     * A proxy to the job scheduler service.
     */
    protected final HarvestJobSchedulerService myHarvestJobSchedulerService;

    /**
     * @param aVertx A Vert.x instance
     */
    protected AbstractJobRequestHandler(final Vertx aVertx) {
        myHarvestJobSchedulerService = HarvestJobSchedulerService.createProxy(aVertx);
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
    }

    /**
     * Handle any errors that arise in the course of handling a request.
     *
     * @param aContext The context on which the error happened
     * @param anError The error that triggered this handler
     */
    @SuppressWarnings("PMD.AvoidPrintStackTrace")
    protected void handleError(final RoutingContext aContext, final Throwable anError) {
        final HttpServerResponse response = aContext.response();
        final int statusCode;

        if (anError instanceof HarvestScheduleStoreServiceException) {
            final HarvestScheduleStoreServiceException serviceException =
                    (HarvestScheduleStoreServiceException) anError;

            statusCode = switch (HarvestScheduleStoreService.Error.values()[serviceException.failureCode()]) {
                case NOT_FOUND -> {
                    yield HttpStatus.SC_NOT_FOUND;
                }
                case INTERNAL_ERROR -> {
                    yield HttpStatus.SC_INTERNAL_SERVER_ERROR;
                }
            };
        } else {
            statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }

        response.setStatusCode(statusCode).end(anError.getMessage());

        anError.printStackTrace();
    }
}
