
package edu.ucla.library.prl.harvester.handlers;

import org.apache.solr.client.solrj.response.UpdateResponse;

import edu.ucla.library.prl.harvester.Config;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

/**
 * An abstract base class for write operation request handlers that need to update Solr.
 */
public abstract class AbstractSolrAwareWriteOperationHandler extends AbstractRequestHandler {

    /**
     * A client for sending institution records to Solr.
     */
    protected final JavaAsyncSolrClient mySolrClient;

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    protected AbstractSolrAwareWriteOperationHandler(final Vertx aVertx, final JsonObject aConfig) {
        super(aVertx);

        mySolrClient = JavaAsyncSolrClient.create(aConfig.getString(Config.SOLR_CORE_URL));
    }

    /**
     * Performs a Solr update appropriate for the type of incoming request.
     *
     * @param aData The relevant data for the Solr operation; implementers may use as needed and are responsible for
     *        reading it carefully
     * @return The result of performing the Solr update
     */
    abstract Future<UpdateResponse> updateSolr(Tuple aData);
}
