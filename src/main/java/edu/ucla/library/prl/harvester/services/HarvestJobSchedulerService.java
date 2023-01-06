
package edu.ucla.library.prl.harvester.services;

import org.quartz.SchedulerException;

import edu.ucla.library.prl.harvester.Job;

import info.freelibrary.util.StringUtils;

import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
     * The event bus address that the service will publish job results to.
     */
    String JOB_RESULT_ADDRESS = StringUtils.format("{}.job_results", ADDRESS);

    /**
     * The event bus address that the service will publish errors to.
     */
    String ERROR_ADDRESS = StringUtils.format("{}.errors", ADDRESS);

    /**
     * Asynchronously instantiates the service.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @return A Future that resolves to the service if it could be instantiated
     */
    static Future<HarvestJobSchedulerService> create(final Vertx aVertx, final JsonObject aConfig) {
        final Promise<HarvestJobSchedulerService> promise = Promise.promise();

        try {
            new HarvestJobSchedulerServiceImpl(aVertx, aConfig, promise);
        } catch (SchedulerException details) {
            promise.fail(details);
        }

        return promise.future();
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
    Future<Integer> addJob(Job aJob);

    /**
     * Updates a harvest job.
     *
     * @param aJobId The unique local ID for the harvest job
     * @param aJob The harvest job to replace the existing one with
     * @return A Future that succeeds if the harvest job was updated
     */
    Future<Void> updateJob(int aJobId, Job aJob);

    /**
     * Removes a harvest job.
     *
     * @param aJobId The unique local ID for the harvest job
     * @return A Future that succeeds if the harvest job was removed
     */
    Future<Void> removeJob(int aJobId);

    /**
     * Closes the underlying resources used by this service.
     *
     * @return A Future that succeeds once the resources have been closed
     */
    @ProxyClose
    Future<Void> close();
}
