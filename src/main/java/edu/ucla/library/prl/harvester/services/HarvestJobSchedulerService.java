
package edu.ucla.library.prl.harvester.services;

import java.util.NoSuchElementException;

import edu.ucla.library.prl.harvester.Job;

import info.freelibrary.util.StringUtils;

import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
     * The {@link JobDataMap} key for the JSON-encoded harvest job.
     */
    String ENCODED_JOB_JSON = "encodedJobJSON";

    /**
     * The {@link JobDataMap} key for the Vert.x context.
     */
    String VERTX_CONTEXT = "vertxContext";

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
     * @param aJob The harvest job to add; this job must have a {@link Job.ID} key
     * @return A Future that succeeds if the harvest job was added
     * @throws {@link NoSuchElementException} If {@code aJob.getID().isEmpty() == true}
     */
    Future<Void> addJob(Job aJob);

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
