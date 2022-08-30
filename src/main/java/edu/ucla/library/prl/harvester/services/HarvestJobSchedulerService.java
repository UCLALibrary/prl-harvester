
package edu.ucla.library.prl.harvester.services;

import edu.ucla.library.prl.harvester.Job;

import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceProxyBuilder;

/**
 * The interface of the event bus service that schedules harvest jobs.
 */
@ProxyGen
@VertxGen
public interface HarvestJobSchedulerService {

    /**
     * The event bus address that the service will be registered on, for access via service proxies.
     */
    String ADDRESS = HarvestJobSchedulerService.class.getName();

    /**
     * Creates an instance of the service.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @return The service instance
     */
    static HarvestJobSchedulerService create(final Vertx aVertx, final JsonObject aConfig) {
        // FIXME: this is incorrect, instantiate an implementing class instead
        // TODO: depending on implementation, consider returning Future<HarvestJobSchedulerService> instead
        return createProxy(aVertx);
    }

    /**
     * Creates an instance of the service proxy.
     *
     * @param aVertx A Vert.x instance
     * @return A service proxy instance
     */
    static HarvestJobSchedulerService createProxy(final Vertx aVertx) {
        return new ServiceProxyBuilder(aVertx).setAddress(ADDRESS).build(HarvestJobSchedulerService.class);
    }

    /**
     * Adds a harvest job.
     *
     * @param aJob The harvest job to add
     * @return A Future that succeeds, with a unique local ID for the harvest job, if it was added
     */
    Future<Integer> addHarvestJob(Job aJob);

    /**
     * Updates a harvest job.
     *
     * @param aJobId The unique local ID for the harvest job
     * @param aJob The harvest job to replace the existing one with
     * @return A Future that succeeds if the harvest job was updated
     */
    Future<Void> updateHarvestJob(int aJobId, Job aJob);

    /**
     * Removes a harvest job.
     *
     * @param aJobId The unique local ID for the harvest job
     * @return A Future that succeeds if the harvest job was removed
     */
    Future<Void> removeHarvestJob(int aJobId);

    /**
     * Closes the underlying resources used by this service.
     *
     * @return A Future that succeeds once the resources have been closed
     */
    @ProxyClose
    Future<Void> close();
}
