
package edu.ucla.library.prl.harvester.handlers;

import edu.ucla.library.prl.harvester.services.HarvestJobSchedulerService;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

/**
 * An abstract base class for request handlers.
 */
public abstract class AbstractRequestHandler implements Handler<RoutingContext> {

    /**
     * The Vert.x instance.
     */
    protected final Vertx myVertx;

    /**
     * A proxy to the harvest schedule store service.
     */
    protected final HarvestScheduleStoreService myHarvestScheduleStoreService;

    /**
     * A proxy to the job scheduler service.
     */
    protected final HarvestJobSchedulerService myHarvestJobSchedulerService;

    /**
     * @param aVertx A Vert.x instance
     */
    protected AbstractRequestHandler(final Vertx aVertx) {
        myHarvestJobSchedulerService = HarvestJobSchedulerService.createProxy(aVertx);
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
        myVertx = aVertx;
    }
}
