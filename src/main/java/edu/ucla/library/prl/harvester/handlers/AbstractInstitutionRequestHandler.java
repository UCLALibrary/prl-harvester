
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.serviceproxy.ServiceException;

/**
 * An abstract base class for request handlers that deal with {@link Institution}s.
 */
public abstract class AbstractInstitutionRequestHandler implements Handler<RoutingContext> {

    /**
     * A proxy to the harvest schedule store service.
     */
    protected final HarvestScheduleStoreService myHarvestScheduleStoreService;

    /**
     * @param aVertx A Vert.x instance
     */
    protected AbstractInstitutionRequestHandler(final Vertx aVertx) {
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
    }

    /**
     * Handle any errors that arise in the course of handling a request.
     *
     * @param aContext The context on which the error happened
     * @param anError The error that triggered this handler
     */
    protected void handleError(final RoutingContext aContext, final Throwable anError) {
        final HttpServerResponse response = aContext.response();

        try {
            final ServiceException serviceException = (ServiceException) anError;
            final int statusCode = switch (HarvestScheduleStoreService.Error.values()[serviceException.failureCode()]) {
                case NOT_FOUND -> {
                    yield HttpStatus.SC_NOT_FOUND;
                }
                case INTERNAL_ERROR -> {
                    yield HttpStatus.SC_INTERNAL_SERVER_ERROR;
                }
            };

            response.setStatusCode(statusCode).end(serviceException.getMessage());
        } catch (final ClassCastException details) {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).end(details.getMessage());
        }
    }
}
