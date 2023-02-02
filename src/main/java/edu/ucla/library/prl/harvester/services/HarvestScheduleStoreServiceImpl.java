
package edu.ucla.library.prl.harvester.services;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;

import io.vertx.core.Future;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
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
        SELECT id, name, description, location, email, phone, webContact AS "webContact", website
        FROM public.institutions
        WHERE id = $1
        """;

    /**
     * The insert query for institutions.
     */
    private static final String ADD_INST = """
        INSERT INTO public.institutions (name, description, location, email, phone, webContact, website)
        VALUES (#{name}, #{description}, #{location}, #{email}, #{phone}, #{webContact}, #{website})
        RETURNING id
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
    private static final String ADD_JOB = """
        INSERT INTO public.harvestjobs (
            institutionID, repositoryBaseURL, metadataPrefix, sets, lastSuccessfulRun, scheduleCronExpression
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

    // See: https://vertx.io/docs/vertx-sql-client-templates/java/#_mapping_with_jackson_databind
    static {
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
    }

    HarvestScheduleStoreServiceImpl(final Pool aDbConnectionPool) {
        myDbConnectionPool = aDbConnectionPool;
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
    public Future<Integer> addInstitution(final Institution anInstitution) {
        return myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, ADD_INST).mapFrom(INST_MAPPER).execute(anInstitution);
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_006, error.getMessage());

            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
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
    public Future<Integer> addJob(final Job aJob) {
        return myDbConnectionPool.withConnection(connection -> {
            return SqlTemplate.forQuery(connection, ADD_JOB).mapFrom(JOB_MAPPER).execute(aJob);
        }).recover(error -> {
            LOGGER.error(MessageCodes.PRL_009, error.getMessage());
            return Future
                    .failedFuture(new HarvestScheduleStoreServiceException(Error.INTERNAL_ERROR, error.getMessage()));
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
                    LOGGER.getMessage(MessageCodes.PRL_015, aJobId)));
        });
    }

    @Override
    public Future<Void> close() {
        return Future.succeededFuture();
    }
}
