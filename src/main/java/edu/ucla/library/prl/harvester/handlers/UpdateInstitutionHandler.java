
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.InvalidInstitutionJsonException;
import edu.ucla.library.prl.harvester.MediaType;
import edu.ucla.library.prl.harvester.Param;

import io.vavr.Tuple;
import io.vavr.Tuple1;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for updating institutions.
 */
public final class UpdateInstitutionHandler extends AbstractSolrAwareWriteOperationHandler<Tuple1<Institution>> {

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
            final Institution institution = new Institution(aContext.body().asJsonObject());
            final Institution institutionWithID = Institution.withID(institution, id);

            myHarvestScheduleStoreService.updateInstitution(id, institution).compose(nil -> {
                return updateSolr(Tuple.of(institutionWithID));
            }).onSuccess(result -> {
                final JsonObject responseBody = institutionWithID.toJson();

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
     * @param aData A 1-tuple of the institution, bearing a unique local ID, to update
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple1<Institution> aData) {
        return AddInstitutionsHandler.updateInstitutionDoc(mySolrClient, List.of(aData._1()));
    }
}
