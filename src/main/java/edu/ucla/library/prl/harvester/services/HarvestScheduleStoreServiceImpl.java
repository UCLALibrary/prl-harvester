
package edu.ucla.library.prl.harvester.services;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.List;
import java.util.Optional;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.Row;
import io.vertx.serviceproxy.ServiceException;

/**
 * The implementation of {@link HarvestScheduleStoreService}.
 */
public class HarvestScheduleStoreServiceImpl implements HarvestScheduleStoreService {

    /**
     * The schedule store service's logger.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(HarvestScheduleStoreService.class, MessageCodes.BUNDLE);

    /**
     * The select-one query for institutions.
     */
    private static final String GET_INST = "SELECT * FROM public.institutions WHERE id = $1";

    /**
     * The insert query for institutions.
     */
    private static final String ADD_INST = "INSERT INTO public.institutions(name, description, location, email," +
            " phone, webContact, website) VALUES($1, $2, $3, $4, $5, $6, $7) RETURNING id";

    /**
     * The postgres database (and default user) name.
     */
    private static final String POSTGRES = "postgres";

    /**
     * The database's default hostname.
     */
    private static final String DEFAULT_HOSTNAME = "localhost";

    /**
     * The underlying PostgreSQL connection pool.
     */
    private final PgPool myDbConnectionPool;

    HarvestScheduleStoreServiceImpl(final Vertx aVertx, final JsonObject aConfig) {
        myDbConnectionPool = PgPool.pool(aVertx, getConnectionOpts(aConfig), getPoolOpts(aConfig));
    }

    @Override
    public Future<Institution> getInstitution(final int anInstitutionId) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(GET_INST).execute(Tuple.of(anInstitutionId));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            if (hasSingleRow(select)) {
                return Future.succeededFuture(select.iterator().next().getInteger("access_mode"));
            }
            return Future.failedFuture(new ServiceException(NOT_FOUND_ERROR, anInstitutionId));
        });
    }

    @Override
    public Future<List<Institution>> listInstitutions() {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Integer> addInstitution(final Institution anInstitution) {
        final StringBuffer newID = new StringBuffer();
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(ADD_INST)
                    .execute(Tuple.of(anInstitution.getName(), anInstitution.getDescription(),
                            anInstitution.getLocation(), getOptionalAsString(anInstitution.getEmail()),
                            getOptionalAsString(anInstitution.getPhone()),
                            getOptionalAsString(anInstitution.getWebContact()), anInstitution.getWebsite().toString()))
                    .onSuccess(rows -> {
                        // n.b. the query should return just one result, but there doesn't seem
                        // to be a way to grab a specific row out of a RowSet<Row>
                        for (final Row row : rows) {
                            newID.append(row.getInteger("id"));
                        }
                    });
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_006, error.getMessage());
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(result -> Future.succeededFuture(Integer.valueOf(newID.toString())));
    }

    /**
     * Converts Optional values to String for use in prepared queries.
     *
     * @param aParam An Optional used as a query param
     * @return The String representation of the Optional value, or an empty string if Optional is empty
     */
    private String getOptionalAsString(final Optional aParam) {
        if (aParam.isPresent()) {
            return aParam.get().toString();
        } else {
            return String.valueOf("");
        }
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
        return myDbConnectionPool.close();
    }

    /**
     * Gets the database's connection options.
     *
     * @param aConfig A configuration
     * @return The database's connection options
     */
    private PgConnectOptions getConnectionOpts(final JsonObject aConfig) {
        final String dbHost = aConfig.getString(Config.DB_HOST, DEFAULT_HOSTNAME);
        final int dbPort = aConfig.getInteger(Config.DB_PORT, 5432);
        final String dbName = aConfig.getString(Config.DB_NAME, POSTGRES);
        final String dbUser = aConfig.getString(Config.DB_USERNAME, POSTGRES);
        final String dbPassword = aConfig.getString(Config.DB_PASSWORD);
        final int dbReconnectAttempts = aConfig.getInteger(Config.DB_RECONNECT_ATTEMPTS, 2);
        final long dbReconnectInterval = aConfig.getInteger(Config.DB_RECONNECT_INTERVAL, 1000);

        return new PgConnectOptions().setPort(dbPort).setHost(dbHost).setDatabase(dbName).setUser(dbUser)
                .setPassword(dbPassword).setReconnectAttempts(dbReconnectAttempts)
                .setReconnectInterval(dbReconnectInterval);
    }

    /**
     * Gets the options for the database connection pool.
     *
     * @param aConfig A configuration
     * @return The options for the database connection pool
     */
    private PoolOptions getPoolOpts(final JsonObject aConfig) {
        final int maxSize = aConfig.getInteger(Config.DB_CONNECTION_POOL_MAX_SIZE, 5);

        return new PoolOptions().setMaxSize(maxSize);
    }

}
