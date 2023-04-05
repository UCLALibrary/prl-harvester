
package edu.ucla.library.prl.harvester.handlers;

import java.nio.file.Path;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that performs an HTTP redirect.
 */
public class SimpleRedirectHandler implements Handler<RoutingContext> {

    /**
     * The HTTP status code to use, typically 301 or 302.
     */
    private final int myStatusCode;

    /**
     * The path to redirect to.
     */
    private final Path myTargetPath;

    /**
     * @param aStatusCode The HTTP status code to use, typically 301 or 302
     * @param aTargetPath The path to redirect to
     */
    public SimpleRedirectHandler(final int aStatusCode, final Path aTargetPath) {
        myStatusCode = aStatusCode;
        myTargetPath = aTargetPath;
    }

    @Override
    public void handle(final RoutingContext aContext) {
        aContext.response().setStatusCode(myStatusCode).putHeader(HttpHeaders.LOCATION, myTargetPath.toString()).end();
    }
}
