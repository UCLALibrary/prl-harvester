
package edu.ucla.library.prl.harvester.services;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Error;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import io.vertx.sqlclient.templates.TupleMapper;
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
     * A template parameter mapper for {@link Institution}.
     */
    private static final TupleMapper<Institution> INST_MAPPER =
            TupleMapper.mapper(Institution::toSqlTemplateParametersMap);

    /**
     * A template parameter mapper for {@link Job}.
     */
    private static final TupleMapper<Job> JOB_MAPPER = TupleMapper.mapper(Job::toSqlTemplateParametersMap);

    /**
     * The select-one query for institutions.
     */
    private static final String GET_INST = """
        SELECT name, description, location, email, phone, webcontact AS "webContact", website
        FROM public.institutions
        WHERE id = $1
        """;

    /**
     * The insert query for institutions.
     */
    private static final String ADD_INST = """
        INSERT INTO public.institutions (name, description, location, email, phone, webcontact, website)
        VALUES (#{name}, #{description}, #{location}, #{email}, #{phone}, #{webContact}, #{website})
        RETURNING id
        """;

    /**
     * The select query for all institutions.
     */
    private static final String LIST_INSTS = """
        SELECT id, name, description, location, email, phone, webcontact AS "webContact", website
        FROM public.institutions
        ORDER BY name
        """;

    /**
     * The delete query for an institution.
     */
    private static final String DEL_INST = "DELETE FROM public.institutions WHERE id = $1";

    /**
     * The update query for an institution.
     */
    private static final String UPDATE_INST = """
        UPDATE public.institutions
        SET name = #{name}, description = #{description}, location = #{location}, email = #{email}, phone = #{phone},
            webcontact = #{webContact}, website = #{website}
        WHERE id = #{id}
        """;

    /**
     * The select-one query for jobs.
     */
    private static final String GET_JOB = """
        SELECT
            institutionid AS "institutionID", repositorybaseurl AS "repositoryBaseURL",
            metadataprefix AS "metadataPrefix", sets, lastsuccessfulrun AS "lastSuccessfulRun",
            schedulecronexpression AS "scheduleCronExpression"
        FROM public.harvestjobs
        WHERE id = $1
        """;

    /**
     * The insert query for jobs.
     */
    private static final String ADD_JOB = """
        INSERT INTO public.harvestjobs (
            institutionid, repositorybaseurl, metadataprefix, sets, lastsuccessfulrun, schedulecronexpression
        )
        VALUES (
            #{institutionID}, #{repositoryBaseURL}, #{metadataPrefix}, #{sets}, #{lastSuccessfulRun},
            #{scheduleCronExpression}
        )
        RETURNING id
        """;

    /**
     * The select query for all jobs.
     */
    private static final String LIST_JOBS = """
        SELECT
            id, institutionid AS "institutionID", repositorybaseurl AS "repositoryBaseURL",
            metadataprefix AS "metadataPrefix", sets, lastsuccessfulrun AS "lastSuccessfulRun",
            schedulecronexpression AS "scheduleCronExpression"
        FROM public.harvestjobs
        ORDER BY "institutionID"
        """;

    /**
     * The delete query for a job.
     */
    private static final String DEL_JOB = "DELETE FROM public.harvestjobs WHERE id = $1";

    /**
     * The update query for a job.
     */
    private static final String UPDATE_JOB = """
        UPDATE public.harvestjobs
        SET
            repositorybaseurl = #{repositoryBaseURL}, sets = #{sets}, lastsuccessfulrun = #{lastSuccessfulRun},
            schedulecronexpression = #{scheduleCronExpression}
        WHERE id = #{id} AND institutionid = #{institutionID}
        """;

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

    // See: https://vertx.io/docs/vertx-sql-client-templates/java/#_mapping_with_jackson_databind
    static {
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
    }

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
     * Checks if the given SqlResult represents a single affected row or not.
     *
     * @param aSqlResult A SqlResult representing the response to a database operation
     * @return true if it represents a single affected row, false otherwise
     */
    private static boolean hasSingleRow(final SqlResult<?> aSqlResult) {
        return aSqlResult.rowCount() == 1;
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
            return SqlTemplate.forQuery(connection, ADD_INST).mapFrom(INST_MAPPER).execute(anInstitution);
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_006, error.getMessage());
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(insert -> {
            return Future.succeededFuture(insert.iterator().next().getInteger(Institution.ID));
        });
    }

    @Override
    public Future<Void> updateInstitution(final int anInstitutionId, final Institution anInstitution) {
        return myDbConnectionPool.withConnection(connection -> {
            final Institution institutionWithID = Institution.withID(anInstitution, anInstitutionId);

            return SqlTemplate.forUpdate(connection, UPDATE_INST).mapFrom(INST_MAPPER).execute(institutionWithID);
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(update -> {
            if (hasSingleRow(update)) {
                return Future.succeededFuture();
            }
            return Future.failedFuture(
                    new ServiceException(NOT_FOUND_ERROR, LOGGER.getMessage(MessageCodes.PRL_019, anInstitutionId)));
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
                    new ServiceException(NOT_FOUND_ERROR, LOGGER.getMessage(MessageCodes.PRL_010, aJobId)));
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
            return SqlTemplate.forQuery(connection, ADD_JOB).mapFrom(JOB_MAPPER).execute(aJob);
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_009, error.getMessage());
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(insert -> {
            return Future.succeededFuture(insert.iterator().next().getInteger(Job.ID));
        });
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        return myDbConnectionPool.withConnection(connection -> {
            final Job jobWithID = Job.withID(aJob, aJobId);

            return SqlTemplate.forUpdate(connection, UPDATE_JOB).mapFrom(JOB_MAPPER).execute(jobWithID);
        }).recover(error -> {
            return Future.failedFuture(new ServiceException(500, error.getMessage()));
        }).compose(update -> {
            if (hasSingleRow(update)) {
                return Future.succeededFuture();
            }
            return Future.failedFuture(new ServiceException(NOT_FOUND_ERROR,
                    LOGGER.getMessage(MessageCodes.PRL_015, aJobId, aJob.getInstitutionID())));
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
        final int dbReconnectAttempts = aConfig.getInteger(Config.DB_RECONNECT_ATTEMPTS, 2);
        final long dbReconnectInterval = aConfig.getInteger(Config.DB_RECONNECT_INTERVAL, 1000);

        return PgConnectOptions.fromEnv() //
                .setReconnectAttempts(dbReconnectAttempts) //
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
