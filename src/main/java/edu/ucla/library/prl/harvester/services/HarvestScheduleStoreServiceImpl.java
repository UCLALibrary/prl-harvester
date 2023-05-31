
package edu.ucla.library.prl.harvester.services;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.JobResult;
import edu.ucla.library.prl.harvester.MessageCodes;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.templates.RowMapper;
import io.vertx.sqlclient.templates.SqlTemplate;
import io.vertx.sqlclient.templates.TupleMapper;

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
    private static final TupleMapper<Institution> INST_TO_TUPLE =
            TupleMapper.mapper(Institution::toSqlTemplateParametersMap);

    /**
     * A template parameter mapper for {@link Job}.
     */
    private static final TupleMapper<Job> JOB_TO_TUPLE = TupleMapper.mapper(Job::toSqlTemplateParametersMap);

    /**
     * A template parameter mapper for either {@link Institution} or {@link Job} IDs.
     */
    private static final TupleMapper<Integer> ID_TO_TUPLE = TupleMapper.mapper(id -> Map.of("id", id));

    /**
     * A row mapper for {@link Institution}.
     */
    private static final RowMapper<Institution> INST_FROM_ROW = row -> new Institution(row.toJson());

    /**
     * A row mapper for {@link Job}.
     */
    private static final RowMapper<Job> JOB_FROM_ROW = row -> new Job(row.toJson());

    /**
     * The select-one query for institutions.
     */
    private static final String GET_INST = """
        SELECT id, name, description, location, email, phone, webContact AS "webContact", website
        FROM public.institutions
        WHERE id = #{id}
        """;

    /**
     * The insert query for institutions.
     */
    private static final String ADD_INSTS = """
        INSERT INTO public.institutions (name, description, location, email, phone, webContact, website)
        VALUES (#{name}, #{description}, #{location}, #{email}, #{phone}, #{webContact}, #{website})
        RETURNING id, name, description, location, email, phone, webContact AS "webContact", website
        """;

    /**
     * The select query for all institutions.
     */
    private static final String LIST_INSTS = """
        SELECT id, name, description, location, email, phone, webContact AS "webContact", website
        FROM public.institutions
        ORDER BY name
        """;

    /**
     * The delete query for an institution.
     */
    private static final String DEL_INST = "DELETE FROM public.institutions WHERE id = #{id}";

    /**
     * The update query for an institution.
     */
    private static final String UPDATE_INST = """
        UPDATE public.institutions
        SET name = #{name}, description = #{description}, location = #{location}, email = #{email}, phone = #{phone},
            webContact = #{webContact}, website = #{website}
        WHERE id = #{id}
        """;

    /**
     * The select-one query for jobs.
     */
    private static final String GET_JOB = """
        SELECT
            id, institutionID AS "institutionID", repositoryBaseURL AS "repositoryBaseURL",
            metadataPrefix AS "metadataPrefix", sets, lastSuccessfulRun AS "lastSuccessfulRun",
            scheduleCronExpression AS "scheduleCronExpression"
        FROM public.harvestjobs
        WHERE id = #{id}
        """;

    /**
     * The insert query for jobs.
     */
    private static final String ADD_JOBS = """
        INSERT INTO public.harvestjobs (
            institutionID, repositoryBaseURL, metadataPrefix, sets, lastSuccessfulRun, scheduleCronExpression
        )
        VALUES (
            #{institutionID}, #{repositoryBaseURL}, #{metadataPrefix}, #{sets}, #{lastSuccessfulRun},
            #{scheduleCronExpression}
        )
        RETURNING
            id, institutionID AS "institutionID", repositoryBaseURL AS "repositoryBaseURL",
            metadataPrefix AS "metadataPrefix", sets, lastSuccessfulRun AS "lastSuccessfulRun",
            scheduleCronExpression AS "scheduleCronExpression"
        """;

    /**
     * The select query for all jobs.
     */
    private static final String LIST_JOBS = """
        SELECT
            id, institutionID AS "institutionID", repositoryBaseURL AS "repositoryBaseURL",
            metadataPrefix AS "metadataPrefix", sets, lastSuccessfulRun AS "lastSuccessfulRun",
            scheduleCronExpression AS "scheduleCronExpression"
        FROM public.harvestjobs
        ORDER BY "institutionID"
        """;

    /**
     * The delete query for a job.
     */
    private static final String DEL_JOB = "DELETE FROM public.harvestjobs WHERE id = #{id}";

    /**
     * The update query for a job.
     */
    private static final String UPDATE_JOB = """
        UPDATE public.harvestjobs
        SET
        repositoryBaseURL = #{repositoryBaseURL}, sets = #{sets}, lastSuccessfulRun = #{lastSuccessfulRun},
            scheduleCronExpression = #{scheduleCronExpression}
        WHERE id = #{id} AND institutionID = #{institutionID}
        """;

    /**
     * The underlying database connection pool.
     */
    private final Pool myDbConnectionPool;

    /**
     * A handler that listens for job results and updates the database accordingly.
     */
    private final MessageConsumer<JsonObject> myJobResultHandler;

    // See: https://vertx.io/docs/vertx-sql-client-templates/java/#_mapping_with_jackson_databind
    static {
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
    }

    HarvestScheduleStoreServiceImpl(final Vertx aVertx, final Pool aDbConnectionPool) {
        myDbConnectionPool = aDbConnectionPool;

        // Listen for completed jobs and update the database with the start time of the job's last successful run
        myJobResultHandler = aVertx.eventBus().consumer(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, message -> {
            final JobResult jobResult = new JobResult(message.body());
            final int jobID = jobResult.getJobID();

            getJob(jobID).compose(job -> {
                final Job withNewLastSuccessfulTime = new Job(job.getInstitutionID(), job.getRepositoryBaseURL(),
                        job.getSets(), job.getScheduleCronExpression(), jobResult.getStartTime());

                return updateJob(jobID, withNewLastSuccessfulTime);
            });
        });
    }

    @Override
    public Future<Institution> getInstitution(final Integer anInstitutionId) {
        final Future<RowSet<Institution>> queryExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, GET_INST).mapFrom(ID_TO_TUPLE).mapTo(INST_FROM_ROW)
                    .execute(anInstitutionId);
        });

        return queryExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            if (hasSingleRow(select)) {
                return Future.succeededFuture(select.iterator().next());
            }
            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.NOT_FOUND,
                    LOGGER.getMessage(MessageCodes.PRL_007, anInstitutionId)));
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
        final Future<RowSet<Institution>> queryExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, LIST_INSTS).mapTo(INST_FROM_ROW).execute(Map.of());
        });

        return queryExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).map(HarvestScheduleStoreServiceImpl::<Institution>mergeResults);
    }

    @Override
    public Future<List<Institution>> addInstitutions(final List<Institution> anInstitutions) {
        final Future<RowSet<Institution>> queryExecution;

        if (anInstitutions.isEmpty()) {
            final String errorMsg = LOGGER.getMessage(MessageCodes.PRL_046);

            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.BAD_REQUEST, errorMsg));
        }

        queryExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, ADD_INSTS).mapFrom(INST_TO_TUPLE).mapTo(INST_FROM_ROW)
                    .executeBatch(anInstitutions);
        });

        return queryExecution.recover(error -> {
            LOGGER.error(MessageCodes.PRL_006, error.getMessage());

            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).map(HarvestScheduleStoreServiceImpl::<Institution>mergeResults);
    }

    @Override
    public Future<Void> updateInstitution(final int anInstitutionId, final Institution anInstitution) {
        final Future<SqlResult<Void>> updateExecution = myDbConnectionPool.withConnection(connection -> {
            final Institution institutionWithID = Institution.withID(anInstitution, anInstitutionId);

            return SqlTemplate.forUpdate(connection, UPDATE_INST).mapFrom(INST_TO_TUPLE).execute(institutionWithID);
        });

        return updateExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(update -> {
            if (hasSingleRow(update)) {
                return Future.succeededFuture();
            }
            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.NOT_FOUND,
                    LOGGER.getMessage(MessageCodes.PRL_019, anInstitutionId)));
        });
    }

    @Override
    public Future<Void> removeInstitution(final Integer anInstitutionId) {
        final Future<SqlResult<Void>> updateExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forUpdate(connection, DEL_INST).mapFrom(ID_TO_TUPLE).execute(anInstitutionId);
        });

        return updateExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(delete -> {
            if (hasSingleRow(delete)) {
                return Future.succeededFuture();
            }
            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.NOT_FOUND,
                    LOGGER.getMessage(MessageCodes.PRL_019, anInstitutionId)));
        });
    }

    @Override
    public Future<Job> getJob(final int aJobId) {
        final Future<RowSet<Job>> queryExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, GET_JOB).mapFrom(ID_TO_TUPLE).mapTo(JOB_FROM_ROW).execute(aJobId);
        });

        return queryExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            if (hasSingleRow(select)) {
                return Future.succeededFuture(select.iterator().next());
            }
            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.NOT_FOUND,
                    LOGGER.getMessage(MessageCodes.PRL_010, aJobId)));
        });
    }

    @Override
    public Future<List<Job>> listJobs() {
        final Future<RowSet<Job>> queryExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, LIST_JOBS).mapTo(JOB_FROM_ROW).execute(Map.of());
        });

        return queryExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).map(HarvestScheduleStoreServiceImpl::<Job>mergeResults);
    }

    @Override
    public Future<List<Job>> addJobs(final List<Job> aJobs) {
        final Future<RowSet<Job>> queryExecution;

        if (aJobs.isEmpty()) {
            final String errorMsg = LOGGER.getMessage(MessageCodes.PRL_046);

            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.BAD_REQUEST, errorMsg));
        }

        queryExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, ADD_JOBS).mapFrom(JOB_TO_TUPLE).mapTo(JOB_FROM_ROW)
                    .executeBatch(aJobs);
        });

        return queryExecution.recover(error -> {
            LOGGER.error(MessageCodes.PRL_009, error.getMessage());
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).map(HarvestScheduleStoreServiceImpl::<Job>mergeResults);
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        final Future<SqlResult<Void>> updateExecution = myDbConnectionPool.withConnection(connection -> {
            final Job jobWithID = Job.withID(aJob, aJobId);

            return SqlTemplate.forUpdate(connection, UPDATE_JOB).mapFrom(JOB_TO_TUPLE).execute(jobWithID);
        });

        return updateExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(update -> {
            if (hasSingleRow(update)) {
                return Future.succeededFuture();
            }
            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.NOT_FOUND,
                    LOGGER.getMessage(MessageCodes.PRL_015, aJobId, aJob.getInstitutionID())));
        });
    }

    @Override
    public Future<Void> removeJob(final int aJobId) {
        final Future<SqlResult<Void>> updateExecution = myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forUpdate(connection, DEL_JOB).mapFrom(ID_TO_TUPLE).execute(aJobId);
        });

        return updateExecution.recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(delete -> {
            if (hasSingleRow(delete)) {
                return Future.succeededFuture();
            }
            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.NOT_FOUND,
                    LOGGER.getMessage(MessageCodes.PRL_014, aJobId)));
        });
    }

    @Override
    public Future<Void> close() {
        return myJobResultHandler.unregister();
    }

    /**
     * @param <U> The type that each row was previously mapped to via {@link SqlTemplate#mapTo(RowMapper)}
     * @param aRowSet The result of executing an SQL query (e.g., the first result obtained via
     *        {@link SqlTemplate#executeBatch(List)}
     * @return A list of objects represented by each row in each row set
     */
    private static <U> List<U> mergeResults(final RowSet<U> aRowSet) {
        final List<U> merged = new LinkedList<>();

        for (RowSet<U> rs = aRowSet; rs != null; rs = rs.next()) {
            final RowIterator<U> it = rs.iterator();

            while (it.hasNext()) {
                merged.add(it.next());
            }
        }

        return merged;
    }
}
