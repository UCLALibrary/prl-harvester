
package edu.ucla.library.prl.harvester.handlers;

import java.util.concurrent.CompletionStage;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.InvalidInstitutionJsonException;
import edu.ucla.library.prl.harvester.MediaType;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

/**
 * A handler for adding institutions.
 */
public final class AddInstitutionHandler extends AbstractSolrAwareWriteOperationHandler {

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public AddInstitutionHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx, aConfig);
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerResponse response = aContext.response();

        try {
            final JsonObject institutionJSON = aContext.body().asJsonObject();
            final Institution institution = new Institution(institutionJSON);

            myHarvestScheduleStoreService.addInstitution(institution).compose(institutionID -> {
                return updateSolr(Tuple.of(institutionID, institutionJSON)).map(institutionID);
            }).onSuccess(institutionID -> {
                final JsonObject responseBody = Institution.withID(institution, institutionID).toJson();

                response.setStatusCode(HttpStatus.SC_CREATED)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .end(responseBody.encode());
            }).onFailure(aContext::fail);
        } catch (final InvalidInstitutionJsonException details) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        }
    }

    /**
     * Adds an institution doc to Solr.
     *
     * @param aData A Tuple of the ID of the institution to update, and its JSON representation
     */
    @Override
    Future<UpdateResponse> updateSolr(final Tuple aData) {
        final Institution institution =
                Institution.withID(new Institution(aData.getJsonObject(1)), aData.getInteger(0));

        return updateInstitutionDoc(mySolrClient, institution);
    }

    /**
     * Adds or updates an institution doc in Solr.
     *
     * @param aSolrClient A Solr client
     * @param anInstitution The institution
     * @return The result of performing the Solr update
     */
    static Future<UpdateResponse> updateInstitutionDoc(final JavaAsyncSolrClient aSolrClient,
            final Institution anInstitution) {
        final CompletionStage<UpdateResponse> addInstitution =
                aSolrClient.addDoc(anInstitution.toSolrDoc()).thenCompose(result -> aSolrClient.commit());

        return Future.fromCompletionStage(addInstitution);
    }
}
