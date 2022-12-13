
package edu.ucla.library.prl.harvester.handlers;

import static edu.ucla.library.prl.harvester.MediaType.APPLICATION_JSON;

import edu.ucla.library.prl.harvester.JsonKeys;

import info.freelibrary.util.HTTP;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that processes status information requests.
 */
public class StatusHandler implements Handler<RoutingContext> {

    @Override
    public void handle(final RoutingContext aContext) {
        aContext.response().setStatusCode(HTTP.OK).putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON.toString())
                .end(new JsonObject().put(JsonKeys.STATUS, "ok").encodePrettily());
    }
}
