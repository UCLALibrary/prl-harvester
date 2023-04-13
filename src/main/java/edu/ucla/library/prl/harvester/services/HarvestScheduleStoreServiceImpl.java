
package edu.ucla.library.prl.harvester.services;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
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
        WHERE id = $1
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
    private static final String DEL_INST = "DELETE FROM public.institutions WHERE id = $1";

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
        WHERE id = $1
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
    private static final String DEL_JOB = "DELETE FROM public.harvestjobs WHERE id = $1";

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
                        job.getSets().orElse(null), job.getScheduleCronExpression(), jobResult.getStartTime());

                return updateJob(jobID, withNewLastSuccessfulTime);
            });
        });
    }

    @Override
    public Future<Institution> getInstitution(final Integer anInstitutionId) {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(GET_INST).execute(Tuple.of(anInstitutionId));
        }).recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            if (hasSingleRow(select)) {
                return Future.succeededFuture(new Institution(select.iterator().next().toJson()));
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
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(LIST_INSTS).execute();
        }).recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
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
    public Future<List<Institution>> addInstitutions(final List<Institution> anInstitutions) {
        return myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, ADD_INSTS).mapFrom(INST_TO_TUPLE).mapTo(INST_FROM_ROW)
                    .executeBatch(anInstitutions);
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_006, error.getMessage());

            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).map(HarvestScheduleStoreServiceImpl::<Institution>mergeResults);
    }

    @Override
    public Future<Void> updateInstitution(final int anInstitutionId, final Institution anInstitution) {
        return myDbConnectionPool.withConnection(connection -> {
            final Institution institutionWithID = Institution.withID(anInstitution, anInstitutionId);

            return SqlTemplate.forUpdate(connection, UPDATE_INST).mapFrom(INST_TO_TUPLE).execute(institutionWithID);
        }).recover(error -> {
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
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(DEL_INST).execute(Tuple.of(anInstitutionId));
        }).recover(error -> {
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
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(GET_JOB).execute(Tuple.of(aJobId));
        }).recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).compose(select -> {
            if (hasSingleRow(select)) {
                return Future.succeededFuture(new Job(select.iterator().next().toJson()));
            }
            return Future.failedFuture(new HarvestScheduleStoreServiceException(Error.NOT_FOUND,
                    LOGGER.getMessage(MessageCodes.PRL_010, aJobId)));
        });
    }

    @Override
    public Future<List<Job>> listJobs() {
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(LIST_JOBS).execute();
        }).recover(error -> {
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
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
    public Future<List<Job>> addJobs(final List<Job> aJobs) {
        return myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, ADD_JOBS).mapFrom(JOB_TO_TUPLE).mapTo(JOB_FROM_ROW)
                    .executeBatch(aJobs);
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_009, error.getMessage());
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
        }).map(HarvestScheduleStoreServiceImpl::<Job>mergeResults);
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        return myDbConnectionPool.withConnection(connection -> {
            final Job jobWithID = Job.withID(aJob, aJobId);

            return SqlTemplate.forUpdate(connection, UPDATE_JOB).mapFrom(JOB_TO_TUPLE).execute(jobWithID);
        }).recover(error -> {
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
        return myDbConnectionPool.withConnection(connection -> {
            return connection.preparedQuery(DEL_JOB).execute(Tuple.of(aJobId));
        }).recover(error -> {
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
