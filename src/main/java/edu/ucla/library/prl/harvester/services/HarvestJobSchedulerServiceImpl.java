
package edu.ucla.library.prl.harvester.services;

import edu.ucla.library.prl.harvester.Job;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The implementation of {@link HarvestScheduleStoreService}.
 */
@SuppressWarnings("PMD.UnusedFormalParameter") //FIXME: temp until constructor defined
public class HarvestJobSchedulerServiceImpl implements HarvestJobSchedulerService {

    HarvestJobSchedulerServiceImpl(final Vertx aVertx, final JsonObject aConfig) {
        // TODO: code constructor
    }

    @Override
    public Future<Integer> addJob(final Job aJob) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> removeJob(final int aJobId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> close() {
        // TODO implement method
        return Future.succeededFuture(null);
    }
}
