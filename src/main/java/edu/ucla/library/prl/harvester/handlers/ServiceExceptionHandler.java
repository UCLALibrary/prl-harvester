
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.services.HarvestJobSchedulerService.HarvestJobSchedulerServiceException;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService.Error;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService.HarvestScheduleStoreServiceException;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.serviceproxy.ServiceException;

/**
 * An error handler for {@link ServiceException}s that arise during API operations on Institutions and Jobs.
 */
public final class ServiceExceptionHandler implements ErrorHandler {

    @Override
    @SuppressWarnings("PMD.AvoidPrintStackTrace")
    public void handle(final RoutingContext aContext) {
        final Throwable error = aContext.failure();
        final int statusCode;

        if (error instanceof HarvestScheduleStoreServiceException) {
            final HarvestScheduleStoreServiceException serviceException = (HarvestScheduleStoreServiceException) error;

            statusCode = switch (Error.values()[serviceException.failureCode()]) {
                case NOT_FOUND -> {
                    yield HttpStatus.SC_NOT_FOUND;
                }
                case INTERNAL_ERROR -> {
                    yield HttpStatus.SC_INTERNAL_SERVER_ERROR;
                }
            };
        } else if (error instanceof HarvestJobSchedulerServiceException) {
            statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        } else {
            // This is not the appropriate error handler for the error
            aContext.next();
            return;
        }

        aContext.response().setStatusCode(statusCode).end(error.getMessage());

        error.printStackTrace();
    }
}
