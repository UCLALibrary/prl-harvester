
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.InvalidInstitutionJsonException;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.Param;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

/**
 * A handler for updating institutions.
 */
public final class UpdateInstitutionHandler extends AbstractSolrAwareWriteOperationHandler {

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public UpdateInstitutionHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx, aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final int id = Integer.parseInt(aContext.request().getParam(Param.id.name()));
            final JsonObject institutionJSON = aContext.body().asJsonObject();
            final Institution institution = new Institution(institutionJSON);

            myHarvestScheduleStoreService.updateInstitution(id, institution).compose(nil -> {
                return updateSolr(Tuple.of(id, institutionJSON));
            }).onSuccess(result -> {
                final JsonObject responseBody = Institution.withID(institution, id).toJson();

                response.setStatusCode(HttpStatus.SC_OK)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .end(responseBody.encode());
            }).onFailure(aContext::fail);
        } catch (final InvalidInstitutionJsonException | NumberFormatException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }

    /**
     * Updates the institution doc in Solr.
     *
     * @param aData A 2-tuple of the ID of the institution to update, and its JSON representation
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple aData) {
        final Institution institution =
                Institution.withID(new Institution(aData.getJsonObject(1)), aData.getInteger(0));

        return AddInstitutionsHandler.updateInstitutionDoc(mySolrClient, List.of(institution));
    }
}
