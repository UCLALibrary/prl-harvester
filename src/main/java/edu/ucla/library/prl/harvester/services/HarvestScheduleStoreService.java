
package edu.ucla.library.prl.harvester.services;

import java.util.List;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;

import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.serviceproxy.ServiceProxyBuilder;
import io.vertx.sqlclient.PoolOptions;

/**
 * The interface of the event bus service that stores the information needed to schedule harvest jobs.
 */
@ProxyGen
@VertxGen
public interface HarvestScheduleStoreService {

    /**
     * The event bus address that the service will be registered on, for access via service proxies.
     */
    String ADDRESS = HarvestScheduleStoreService.class.getName();

    /**
     * Creates an instance of the service.
     *
     * @param aDbConnectionPool A database connection pool
     * @return The service instance
     */
    static HarvestScheduleStoreService create(final PgPool aDbConnectionPool) {
        return new HarvestScheduleStoreServiceImpl(aDbConnectionPool);
    }

    /**
     * Creates an instance of the service proxy.
     *
     * @param aVertx A Vert.x instance
     * @return A service proxy instance
     */
    static HarvestScheduleStoreService createProxy(final Vertx aVertx) {
        return new ServiceProxyBuilder(aVertx).setAddress(ADDRESS).build(HarvestScheduleStoreService.class);
    }

    /**
     * Gets an institution.
     *
     * @param anInstitutionId The unique local ID for the institution
     * @return A Future that succeeds if the institution exists
     */
    Future<Institution> getInstitution(Integer anInstitutionId);

    /**
     * Gets the list of all institutions.
     *
     * @return A Future that succeeds with a list of all institutions (if any); these institutions must each have an
     *         {@link Institution#ID} key
     */
    Future<List<Institution>> listInstitutions();

    /**
     * Adds an institution.
     *
     * @param anInstitution The institution to add
     * @return A Future that succeeds, with a unique local ID for the institution, if it was added
     */
    Future<Integer> addInstitution(Institution anInstitution);

    /**
     * Updates an institution.
     *
     * @param anInstitutionId The unique local ID for the institution
     * @param anInstitution The institution to replace the existing one with
     * @return A Future that succeeds if the institution was updated
     */
    Future<Void> updateInstitution(int anInstitutionId, Institution anInstitution);

    /**
     * Removes an institution and all associated jobs.
     *
     * @param anInstitutionId The unique local ID for the institution
     * @return A Future that succeeds if the institution was removed
     */
    Future<Void> removeInstitution(Integer anInstitutionId);

    /**
     * Gets a harvest job.
     *
     * @param aJobId The unique local ID for the harvest job
     * @return A Future that succeeds if the harvest job exists
     */
    Future<Job> getJob(int aJobId);

    /**
     * Gets the list of all harvest jobs.
     *
     * @return A Future that succeeds with a list of all harvest jobs (if any); these jobs must each have an
     *         {@link Job#ID} key
     */
    Future<List<Job>> listJobs();

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

    /**
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @return A connection pool
     */
    static PgPool getConnectionPool(final Vertx aVertx, JsonObject aConfig) {
        final int dbReconnectAttempts = aConfig.getInteger(Config.DB_RECONNECT_ATTEMPTS, 2);
        final long dbReconnectInterval = aConfig.getInteger(Config.DB_RECONNECT_INTERVAL, 1000);
        final int dbConnectionPoolMaxSize = aConfig.getInteger(Config.DB_CONNECTION_POOL_MAX_SIZE, 5);

        final PgConnectOptions connectOptions = PgConnectOptions.fromEnv() //
                .setReconnectAttempts(dbReconnectAttempts) //
                .setReconnectInterval(dbReconnectInterval);
        final PoolOptions poolOptions = new PoolOptions().setMaxSize(dbConnectionPoolMaxSize);

        return PgPool.pool(aVertx, connectOptions, poolOptions);
    }
}
