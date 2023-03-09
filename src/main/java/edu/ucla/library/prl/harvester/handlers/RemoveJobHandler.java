
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Param;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for removing jobs.
 */
public final class RemoveJobHandler extends AbstractRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     */
    public RemoveJobHandler(final Vertx aVertx) {
        super(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));

            myHarvestScheduleStoreService.removeJob(id).compose(nil -> {
                return myHarvestJobSchedulerService.removeJob(id);
            }).onSuccess(nil -> {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT).end();
            }).onFailure(aContext::fail);
        } catch (final NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }
}
