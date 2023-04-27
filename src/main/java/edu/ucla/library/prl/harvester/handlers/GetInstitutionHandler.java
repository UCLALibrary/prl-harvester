
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.Param;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for getting institutions.
 */
public final class GetInstitutionHandler extends AbstractRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public GetInstitutionHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx, aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));

            myHarvestScheduleStoreService.getInstitution(id).onSuccess(institution -> {
                response.setStatusCode(HttpStatus.SC_OK)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .end(institution.toJson().encode());
            }).onFailure(aContext::fail);
        } catch (final NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }
}
