
package edu.ucla.library.prl.harvester.services;

import edu.ucla.library.prl.harvester.Job;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The implementation of {@link HarvestService}.
 */
public class HarvestServiceImpl implements HarvestService {

    @SuppressWarnings("PMD.UnusedFormalParameter") // FIXME: temp until constructor defined
    HarvestServiceImpl(final Vertx aVertx, final JsonObject aConfig) {
        // TODO: code class constructor
    }

    @Override
    public Future<Object> run(final Job aJob) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> close() {
        // TODO implement method
        return Future.succeededFuture(null);
    }
}
