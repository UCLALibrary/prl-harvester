
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Op;
import edu.ucla.library.prl.harvester.Param;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for {@link Op#removeInstition} operations.
 */
public final class RemoveInstitutionHandler extends AbstractInstitutionRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     */
    public RemoveInstitutionHandler(final Vertx aVertx) {
        super(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));

            myHarvestScheduleStoreService.removeInstitution(id).onSuccess(nil -> {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT).end();
            }).onFailure(details -> handleError(aContext, details));
        } catch (final NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }
}
