
package edu.ucla.library.prl.harvester.handlers;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.InvalidInstitutionJsonException;
import edu.ucla.library.prl.harvester.MediaType;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
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
        final Stream<Future<Institution>> semanticValidations =
                aContext.body().asJsonArray().stream().map(this::validateJob);

        CompositeFuture.all(semanticValidations.collect(Collectors.toList())).onSuccess(result -> {
            final List<Institution> institutions = result.list();

            myHarvestScheduleStoreService.addInstitutions(institutions).compose(institutionsWithIDs -> {
                return updateSolr(Tuple.of(institutionsWithIDs)).map(institutionsWithIDs);
            }).onSuccess(institutionsWithIDs -> {
                final JsonArray responseBody =
                        new JsonArray(institutionsWithIDs.stream().map(Institution::toJson).toList());

                response.setStatusCode(HttpStatus.SC_CREATED)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
                        .end(responseBody.encode());
            }).onFailure(aContext::fail);
        }).onFailure(details -> {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST).end(details.getMessage());
        });
    }

    /**
     * Adds institution docs to Solr.
     *
     * @param aData A 1-tuple of the list of institutions (each bearing a unique local ID) to update
     */
    @Override
    @SuppressWarnings("unchecked")
    Future<UpdateResponse> updateSolr(final Tuple aData) {
        return updateInstitutionDoc(mySolrClient, (List<Institution>) aData.get(List.class, 0));
    }

    /**
     * Adds or updates institution docs in Solr.
     *
     * @param aSolrClient A Solr client
     * @param anInstitutions The list of institutions
     * @return The result of performing the Solr update
     */
    static Future<UpdateResponse> updateInstitutionDoc(final JavaAsyncSolrClient aSolrClient,
            final List<Institution> anInstitutions) {
        final CompletionStage<UpdateResponse> addInstitution =
                aSolrClient.addDocs(anInstitutions.stream().map(Institution::toSolrDoc).toList())
                        .thenCompose(result -> aSolrClient.commit());

        return Future.fromCompletionStage(addInstitution);
    }

    /**
     * @param anInstitution An entry in the request body array
     * @return A Future that succeeds if the entry is a valid {@link Institution}
     */
    private Future<Institution> validateJob(final Object anInstitution) {
        try {
            return Future.succeededFuture(new Institution(JsonObject.mapFrom(anInstitution)));
        } catch (final IllegalArgumentException | InvalidInstitutionJsonException details) {
            return Future.failedFuture(details);
        }
    }
}
