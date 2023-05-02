
package edu.ucla.library.prl.harvester.handlers;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.services.HarvestJobSchedulerService;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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
     * The User-Agent HTTP request header to use for outgoing requests.
     */
    protected final String myHarvesterUserAgent;

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
     * @param aConfig A configuration
     */
    protected AbstractRequestHandler(final Vertx aVertx, final JsonObject aConfig) {
        myHarvesterUserAgent = Config.getHarvesterUserAgent(aConfig);
        myHarvestJobSchedulerService = HarvestJobSchedulerService.createProxy(aVertx);
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
        myVertx = aVertx;
    }

    /**
     * @param aJobID A job ID
     * @return A 2-tuple of the job and its associated institution
     */
    protected Future<Tuple2<Job, Institution>> getJobAndInstitution(final int aJobID) {
        return myHarvestScheduleStoreService.getJob(aJobID).compose(job -> {
            return myHarvestScheduleStoreService.getInstitution(job.getInstitutionID()).map(institution -> {
                return Tuple.of(job, institution);
            });
        });
    }
}
