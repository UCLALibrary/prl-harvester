
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.validation.BadRequestException;

/**
 * An error handler for bad requests that puts the error message in the response.
 */
public final class InformativeBadRequestHandler implements ErrorHandler {

    @Override
    public void handle(final RoutingContext aContext) {
        final Throwable error = aContext.failure();

        if (error instanceof BadRequestException) {
            aContext.response().setStatusCode(HttpStatus.SC_BAD_REQUEST).end(error.getMessage());
        } else {
            aContext.next();
        }
    }
}
