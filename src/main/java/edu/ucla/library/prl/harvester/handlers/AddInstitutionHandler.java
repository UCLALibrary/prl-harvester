
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.InvalidInstitutionJsonException;
import edu.ucla.library.prl.harvester.MediaType;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for adding institutions.
 */
public final class AddInstitutionHandler extends AbstractInstitutionRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     */
    public AddInstitutionHandler(final Vertx aVertx) {
        super(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final Institution institution = new Institution(aContext.body().asJsonObject());

            myHarvestScheduleStoreService.addInstitution(institution).onSuccess(institutionID -> {
                final JsonObject responseBody = Institution.withID(institution, institutionID).toJson();

                response.setStatusCode(HttpStatus.SC_CREATED)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .end(responseBody.encode());
            }).onFailure(details -> handleError(aContext, details));
        } catch (final InvalidInstitutionJsonException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }
}
