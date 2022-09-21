
package edu.ucla.library.prl.harvester.services;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.List;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
//import io.vertx.sqlclient.Row;
import io.vertx.serviceproxy.ServiceException;

/**
 * The implementation of {@link HarvestScheduleStoreService}.
 */
@SuppressWarnings("PMD.UnusedFormalParameter") // FIXME: temp until constructor defined
public class HarvestScheduleStoreServiceImpl implements HarvestScheduleStoreService {

    /**
     * The cschedule store service's logger.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(HarvestScheduleStoreService.class, MessageCodes.BUNDLE);

    /**
     * The underlying PostgreSQL connection pool.
     */
    private final JDBCPool myDbConnectionPool;

    HarvestScheduleStoreServiceImpl(final Vertx aVertx, final JsonObject aConfig) {
        myDbConnectionPool = JDBCPool.pool(aVertx, aConfig);
    }

    @Override
    public Future<Institution> getInstitution(final int anInstitutionId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<List<Institution>> listInstitutions() {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Integer> addInstitution(final Institution anInstitution) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(UPSERT_DEGRADED_ALLOWED).execute(Tuple.of(
                  anInstitution.getName(), anInstitution.getDescription(), anInstitution.getLocation(),
                  anInstitution.getEmail(), anInstitution.getPhone(), anInstitution.getWebContact(),
                  anInstitution.getWebsite()));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(result -> Future.succeededFuture());
    }

    @Override
    public Future<Void> updateInstitution(final int anInstitutionId, final Institution anInstitution) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> removeInstitution(final int anInstitutionId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Job> getJob(final int aJobId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<List<Job>> listJobs() {
        // TODO implement method
        return Future.succeededFuture(null);
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
