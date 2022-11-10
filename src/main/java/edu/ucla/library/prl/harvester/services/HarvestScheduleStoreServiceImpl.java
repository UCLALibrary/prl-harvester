
package edu.ucla.library.prl.harvester.services;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Error;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
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
     * Parses and formats phone numbers.
     */
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

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
     * The select query for all institutions.
     */
    private static final String LIST_INSTS = "SELECT * FROM public.institutions ORDER BY name";

    /**
     * The delete query for an institution.
     */
    private static final String DEL_INST = "DELETE FROM public.institutions WHERE id = $1";

    /**
     * The update query for an institution.
     */
    private static final String UPDATE_INST = "UPDATE public.institutions SET name=$1, description=$2," +
            " location=$3, email=$4, phone=$5, webContact=$6, website=$7 WHERE id = $8";

    /**
     * The select-one query for jobs.
     */
    private static final String GET_JOB = "SELECT * FROM public.harvestjobs WHERE id = $1";

    /**
     * The insert query for jobs.
     */
    private static final String ADD_JOB =
            "INSERT INTO public.harvestjobs(institutionID, repositoryBaseURL, metadataPrefix, sets," +
                    " lastSuccessfulRun, scheduleCronExpression) VALUES($1, $2, $3, $4, $5, $6) RETURNING id";

    /**
     * The select query for all jobs.
     */
    private static final String LIST_JOBS = "SELECT * FROM public.harvestjobs ORDER BY institutionID";

    /**
     * The delete query for a job.
     */
    private static final String DEL_JOB = "DELETE FROM public.harvestjobs WHERE id = $1";

    /**
     * The update query for a job.
     */
    private static final String UPDATE_JOB =
            "UPDATE public.harvestjobs SET repositoryBaseURL=$1, sets=$2, lastSuccessfulRun=$3," +
                    " scheduleCronExpression=$4 WHERE id = $5";

    /**
     * The postgres database (and default user) name.
     */
    private static final String POSTGRES = "postgres";

    /**
     * The database's default hostname.
     */
    private static final String DEFAULT_HOSTNAME = "localhost";

    /**
     * The failure code to use for a ServiceException that represents {@link Error#INTERNAL_ERROR}.
     */
    private static final int INTERNAL_ERROR = Error.INTERNAL_ERROR.ordinal();

    /**
     * The failure code to use for a ServiceException that represents {@link Error#NOT_FOUND}.
     */
    private static final int NOT_FOUND_ERROR = Error.NOT_FOUND.ordinal();

    /**
     * The underlying PostgreSQL connection pool.
     */
    private final PgPool myDbConnectionPool;

    HarvestScheduleStoreServiceImpl(final Vertx aVertx, final JsonObject aConfig) {
        myDbConnectionPool = PgPool.pool(aVertx, getConnectionOpts(aConfig), getPoolOpts(aConfig));
    }

    @Override
    public Future<Institution> getInstitution(final Integer anInstitutionId) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(GET_INST).execute(Tuple.of(anInstitutionId));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            if (hasSingleRow(select)) {
                return Future.succeededFuture(new Institution(select.iterator().next().toJson()));
            }
            return Future.failedFuture(
                    new ServiceException(NOT_FOUND_ERROR, LOGGER.getMessage(MessageCodes.PRL_007, anInstitutionId)));
        });
    }

    /**
     * Checks if the given RowSet consists of a single row or not.
     *
     * @param aRowSet A RowSet representing the response to a database query
     * @return true if it has a single row, false otherwise
     */
    private static boolean hasSingleRow(final RowSet<Row> aRowSet) {
        return aRowSet.rowCount() == 1;
    }

    @Override
    public Future<List<Institution>> listInstitutions() {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(LIST_INSTS).execute();
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            final List<Institution> allInstitutions = new ArrayList<>();
            final RowIterator<Row> iterator = select.iterator();
            while (iterator.hasNext()) {
                allInstitutions.add(new Institution(iterator.next().toJson()));
            }
            return Future.succeededFuture(allInstitutions);
        });
    }

    @Override
    public Future<Integer> addInstitution(final Institution anInstitution) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(ADD_INST)
                    .execute(Tuple.of(anInstitution.getName(), anInstitution.getDescription(),
                            anInstitution.getLocation(), getOptionalAsString(anInstitution.getEmail()),
                            getOptionalPhoneAsString(anInstitution.getPhone()),
                            getOptionalAsString(anInstitution.getWebContact()), anInstitution.getWebsite().toString()));
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_006, error.getMessage());
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(insert -> {
            return Future.succeededFuture(insert.iterator().next().getInteger("id"));
        });
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

    /**
     * Converts Optional phone number values to String for use in prepared queries.
     *
     * @param aPhoneParam An Optional used as a query param
     * @return The String representation of the Optional value, or an empty string if Optional is empty
     */
    private String getOptionalPhoneAsString(final Optional<PhoneNumber> aPhoneParam) {
        if (aPhoneParam.isPresent()) {
            return PHONE_NUMBER_UTIL.format(aPhoneParam.get(), PhoneNumberFormat.INTERNATIONAL);
        } else {
            return String.valueOf("");
        }
    }

    @Override
    public Future<Void> updateInstitution(final int anInstitutionId, final Institution anInstitution) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(UPDATE_INST)
                    .execute(Tuple.of(anInstitution.getName(), anInstitution.getDescription(),
                            anInstitution.getLocation(), getOptionalAsString(anInstitution.getEmail()),
                            getOptionalPhoneAsString(anInstitution.getPhone()),
                            getOptionalAsString(anInstitution.getWebContact()), anInstitution.getWebsite().toString(),
                            anInstitutionId));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(update -> {
            return Future.succeededFuture();
        });
    }

    @Override
    public Future<Void> removeInstitution(final Integer anInstitutionId) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(DEL_INST).execute(Tuple.of(anInstitutionId));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(delete -> {
            return Future.succeededFuture();
        });
    }

    @Override
    public Future<Job> getJob(final int aJobId) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(GET_JOB).execute(Tuple.of(aJobId));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            if (hasSingleRow(select)) {
                return Future.succeededFuture(new Job(select.iterator().next().toJson()));
            }
            return Future.failedFuture(
                    new ServiceException(NOT_FOUND_ERROR, LOGGER.getMessage(MessageCodes.PRL_007, aJobId)));
        });
    }

    @Override
    public Future<List<Job>> listJobs() {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(LIST_JOBS).execute();
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            final List<Job> allJobs = new ArrayList<>();
            final RowIterator<Row> iterator = select.iterator();
            while (iterator.hasNext()) {
                allJobs.add(new Job(iterator.next().toJson()));
            }
            return Future.succeededFuture(allJobs);
        });
    }

    @Override
    public Future<Integer> addJob(final Job aJob) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(ADD_JOB)
                    .execute(Tuple.of(aJob.getInstitutionID(), aJob.getRepositoryBaseURL().toString(),
                            aJob.getMetadataPrefix(), getOptionalAsString(aJob.getSets()),
                            aJob.getScheduleCronExpression().toString(),
                            getOptionalAsString(aJob.getLastSuccessfulRun())));
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_009, error.getMessage());
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(insert -> {
            return Future.succeededFuture(insert.iterator().next().getInteger("id"));
        });
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(UPDATE_JOB)
                    .execute(Tuple.of(aJob.getRepositoryBaseURL().toString(), aJob.getMetadataPrefix(),
                            getOptionalAsString(aJob.getSets()), aJob.getScheduleCronExpression().toString(),
                            getOptionalAsString(aJob.getLastSuccessfulRun()), aJobId));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(update -> {
            return Future.succeededFuture();
        });
    }

    @Override
    public Future<Void> removeJob(final int aJobId) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(DEL_JOB).execute(Tuple.of(aJobId));
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(INTERNAL_ERROR, error.getMessage()));
        }).compose(delete -> {
            return Future.succeededFuture();
        });
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
