
package edu.ucla.library.prl.harvester.handlers;

import org.apache.http.HttpStatus;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.Op;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for {@link Op.listInstitions} operations.
 */
public final class ListInstitutionsHandler extends AbstractInstitutionRequestHandler {

    /**
     * @param aVertx A Vert.x instance
     */
    public ListInstitutionsHandler(final Vertx aVertx) {
        super(aVertx);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        myHarvestScheduleStoreService.listInstitutions().onSuccess(institutions -> {
            final JsonArray responseBody = new JsonArray(institutions.stream().map(Institution::toJson).toList());

            response.setStatusCode(HttpStatus.SC_OK)
                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                    .end(responseBody.encode());
        }).onFailure(details -> handleError(aContext, details));
    }
}
