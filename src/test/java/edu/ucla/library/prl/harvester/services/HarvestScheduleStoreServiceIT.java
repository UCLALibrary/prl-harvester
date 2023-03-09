
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService.Error;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService.HarvestScheduleStoreServiceException;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.i18n.phonenumbers.NumberParseException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.quartz.CronExpression;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.serviceproxy.ServiceException;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.templates.SqlTemplate;
import io.vertx.sqlclient.templates.TupleMapper;

/**
 * Tests {@link HarvestScheduleStoreService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class HarvestScheduleStoreServiceIT {

    /**
     * The schedule store test's logger.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(HarvestScheduleStoreServiceIT.class, MessageCodes.BUNDLE);

    private static final String SAMPLE_NAME = "Sample 1";

    private static final String SAMPLE_CRON = "0 0/30 8-9 5,20 * ?";

    private static final String UPDATE_URL = "http://new.url.com";

    private MessageConsumer<?> myHarvestScheduleStoreService;

    private HarvestScheduleStoreService myScheduleStoreProxy;

    private Pool myDbConnectionPool;

    private List<Integer> myTestInstitutionIDs;

    private List<Integer> myTestJobIDs;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        final ConfigRetriever retriever = ConfigRetriever.create(aVertx);

        retriever.getConfig().compose(config -> {
            final Pool dbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            final HarvestScheduleStoreService service = HarvestScheduleStoreService.create(aVertx, dbConnectionPool);
            final ServiceBinder binder = new ServiceBinder(aVertx);

            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, service);
            myScheduleStoreProxy = HarvestScheduleStoreService.createProxy(aVertx);
            myDbConnectionPool = dbConnectionPool;

            return addInitialInstitutions(dbConnectionPool).compose(institutionIdRowSet -> {
                final Function<Row, Integer> idSelector = row -> row.getInteger("id");
                final List<Integer> institutionIds = getAllValues(institutionIdRowSet, idSelector);

                LOGGER.debug("Institution IDs: {}", institutionIds);

                myTestInstitutionIDs = institutionIds;

                return addInitialJobs(dbConnectionPool, institutionIds).compose(jobIdRowSet -> {
                    final List<Integer> jobIDs = getAllValues(jobIdRowSet, idSelector);

                    LOGGER.debug("Job IDs: {}", jobIDs);

                    myTestJobIDs = jobIDs;

                    return Future.succeededFuture();
                });
            });
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aRowSet A set of results from executing a batch query
     * @param aValueSelector A function that maps a row to a value within it
     * @param <U> The type of the target value
     * @return The result of applying the value selector function to each result
     */
    private static <U> List<U> getAllValues(final RowSet<Row> aRowSet, final Function<Row, U> aValueSelector) {
        final List<U> values = new LinkedList<>();

        RowSet<Row> rowSet = aRowSet;
        do {
            final U value = aValueSelector.apply(rowSet.iterator().next());

            values.add(value);
        } while ((rowSet = rowSet.next()) != null);

        return values;
    }

    /**
     * @param aConnectionPool A database connection pool
     * @return The result of adding the institutions
     */
    private static Future<RowSet<Row>> addInitialInstitutions(final Pool aConnectionPool) {
        try {
            final List<Institution> institutions = List.of(
                    new Institution(SAMPLE_NAME, "A sample institution", "Here",
                            Optional.of(new InternetAddress("this@that.com")), Optional.empty(), Optional.empty(),
                            new URL("http://acme1.edu")),
                    new Institution("Sample 2", "Another sample", "There",
                            Optional.of(new InternetAddress("that@theother.com")), Optional.empty(), Optional.empty(),
                            new URL("http://acme2.edu")),
                    new Institution("Sample 3", "A third sample", "Everywhere",
                            Optional.of(new InternetAddress("no@where.com")), Optional.empty(), Optional.empty(),
                            new URL("http://acme3.edu")));
            final TupleMapper<Institution> tupleMapper = TupleMapper.mapper(Institution::toSqlTemplateParametersMap);
            final String query = """
                INSERT INTO public.institutions (name, description, location, email, phone, webContact, website)
                VALUES (#{name}, #{description}, #{location}, #{email}, #{phone}, #{webContact}, #{website})
                RETURNING id
                """;

            return aConnectionPool.withConnection(connection -> SqlTemplate.forQuery(connection, query)
                    .mapFrom(tupleMapper).executeBatch(institutions));
        } catch (final AddressException | MalformedURLException details) {
            return Future.failedFuture(details);
        }
    }

    /**
     * @param aConnectionPool A database connection pool
     * @param anInstitutionIDs A list of valid institution IDs to associate with jobs
     * @return The result of adding the jobs
     */
    private static Future<RowSet<Row>> addInitialJobs(final Pool aConnectionPool,
            final List<Integer> anInstitutionIDs) {
        try {
            final List<Job> jobs = new LinkedList<>();

            for (final int institutionID : anInstitutionIDs) {
                final Job job = new Job(institutionID, new URL(StringUtils.format("http://acme{}.edu/", institutionID)),
                        null, new CronExpression(SAMPLE_CRON), null);

                jobs.add(job);
            }
            final TupleMapper<Job> tupleMapper = TupleMapper.mapper(Job::toSqlTemplateParametersMap);
            final String query = """
                INSERT INTO public.harvestjobs (
                    institutionID, repositoryBaseURL, metadataPrefix, sets, lastSuccessfulRun, scheduleCronExpression
                )
                VALUES (
                    #{institutionID}, #{repositoryBaseURL}, #{metadataPrefix}, #{sets}, #{lastSuccessfulRun},
                    #{scheduleCronExpression}
                )
                RETURNING id
                """;

            return aConnectionPool.withConnection(
                    connection -> SqlTemplate.forQuery(connection, query).mapFrom(tupleMapper).executeBatch(jobs));
        } catch (final MalformedURLException | ParseException details) {
            return Future.failedFuture(details);
        }
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public final void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        myScheduleStoreProxy.close().compose(result -> {
            return TestUtils.wipeDatabase(myDbConnectionPool).compose(nil -> myDbConnectionPool.close());
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Tests inserting institution record in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testAddInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final Institution toAdd = TestUtils.getRandomInstitution();

        myScheduleStoreProxy.addInstitution(toAdd).onSuccess(institutionID -> {
            aContext.verify(() -> {
                assertTrue(institutionID.intValue() > myTestInstitutionIDs.get(myTestInstitutionIDs.size() - 1));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests getting institution by ID from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int instID = myTestInstitutionIDs.get(0);

        myScheduleStoreProxy.getInstitution(instID).onSuccess(institution -> {
            aContext.verify(() -> {
                assertTrue(institution != null);
                assertTrue(institution.getName().equals(SAMPLE_NAME));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests handling bad institution ID in get institution.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetInstitutionBadID(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int badID = -1;

        myScheduleStoreProxy.getInstitution(badID).onFailure(details -> {
            final ServiceException error = (ServiceException) details;

            aContext.verify(() -> {
                assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                assertTrue(error.getMessage().contains(String.valueOf(badID)));
                assertTrue(details instanceof HarvestScheduleStoreServiceException);

                aContext.completeNow();
            });
        }).onSuccess(result -> {
            aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_011, badID));
        });
    }

    /**
     * Tests getting all institutions from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testListInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        myScheduleStoreProxy.listInstitutions().onSuccess(instList -> {
            aContext.verify(() -> {
                assertTrue(instList != null);
                assertTrue(instList.size() >= 3);
                assertTrue(instList.get(0).getName().equals(SAMPLE_NAME));
                assertTrue(instList.get(0).getID().isPresent());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests deleting institution from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testDeleteInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final Institution toDelete = TestUtils.getRandomInstitution();

        myScheduleStoreProxy.addInstitution(toDelete).onSuccess(newID -> {
            myScheduleStoreProxy.removeInstitution(newID).onSuccess(result -> {
                myScheduleStoreProxy.getInstitution(newID).onFailure(details -> {
                    final ServiceException error = (ServiceException) details;

                    aContext.verify(() -> {
                        assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                        assertTrue(error.getMessage().contains(String.valueOf(newID)));
                        assertTrue(details instanceof HarvestScheduleStoreServiceException);
                    }).completeNow();
                }).onSuccess(select -> {
                    aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_013, newID));
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests updating institution in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testUpdateInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int instID = myTestInstitutionIDs.get(0);

        myScheduleStoreProxy.getInstitution(instID).onSuccess(original -> {
            final Institution modified =
                    new Institution(original.getName(), "changing description", "changing location",
                            original.getEmail(), original.getPhone(), original.getWebContact(), original.getWebsite());

            myScheduleStoreProxy.updateInstitution(instID, modified).onSuccess(result -> {
                myScheduleStoreProxy.getInstitution(instID).onSuccess(updated -> {
                    aContext.verify(() -> {
                        assertTrue(updated.getName().equals(original.getName()));
                        assertTrue(updated.getDescription().equals(modified.getDescription()));
                    }).completeNow();
                }).onFailure(aContext::failNow);
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests getting job by ID from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int jobID = myTestJobIDs.get(0);

        myScheduleStoreProxy.getJob(jobID).onSuccess(job -> {
            aContext.verify(() -> {
                assertTrue(job != null);
                assertTrue(job.getScheduleCronExpression().toString().equals(SAMPLE_CRON));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests handling bad job ID in get job.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetJobBadID(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int badID = -1;

        myScheduleStoreProxy.getJob(badID).onFailure(details -> {
            final ServiceException error = (ServiceException) details;

            aContext.verify(() -> {
                assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                assertTrue(error.getMessage().contains(String.valueOf(badID)));
                assertTrue(details instanceof HarvestScheduleStoreServiceException);

                aContext.completeNow();
            });
        }).onSuccess(result -> {
            aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_012, badID));
        });
    }

    /**
     * Tests inserting job record in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testAddJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException, ParseException {
        final Job toAdd = TestUtils.getRandomJob(myTestInstitutionIDs.get(0));

        myScheduleStoreProxy.addJob(toAdd).onSuccess(result -> {
            aContext.verify(() -> {
                assertTrue(result.intValue() >= 1);
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests getting all jobs from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testListJobs(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        myScheduleStoreProxy.listJobs().onSuccess(jobList -> {
            aContext.verify(() -> {
                assertTrue(jobList != null);
                assertTrue(jobList.size() >= 3);
                assertTrue(jobList.get(0).getScheduleCronExpression().toString().equals(SAMPLE_CRON));
                assertTrue(jobList.get(0).getID().isPresent());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests deleting job from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testDeleteJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException, ParseException {
        final Job toDelete = TestUtils.getRandomJob(myTestInstitutionIDs.get(0));

        myScheduleStoreProxy.addJob(toDelete).onSuccess(newID -> {
            myScheduleStoreProxy.removeJob(newID).onSuccess(result -> {
                myScheduleStoreProxy.getJob(newID).onFailure(details -> {
                    final ServiceException error = (ServiceException) details;

                    aContext.verify(() -> {
                        assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                        assertTrue(error.getMessage().contains(String.valueOf(newID)));
                        assertTrue(details instanceof HarvestScheduleStoreServiceException);
                    }).completeNow();
                }).onSuccess(select -> {
                    aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_014, newID));
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests updating job in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testUpdateJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int jobID = myTestJobIDs.get(0);

        myScheduleStoreProxy.getJob(jobID).onSuccess(original -> {
            try {
                final Job modified = new Job(original.getInstitutionID(), new URL(UPDATE_URL),
                        original.getSets().orElse(null), original.getScheduleCronExpression(), OffsetDateTime.now());

                myScheduleStoreProxy.updateJob(jobID, modified).onSuccess(result -> {
                    myScheduleStoreProxy.getJob(jobID).onSuccess(updated -> {
                        aContext.verify(() -> {
                            assertTrue(updated.getInstitutionID() == original.getInstitutionID());
                            assertTrue(updated.getRepositoryBaseURL().equals(modified.getRepositoryBaseURL()));
                        }).completeNow();
                    }).onFailure(aContext::failNow);
                }).onFailure(aContext::failNow);
            } catch (final MalformedURLException details) {
                aContext.failNow(details);
            }
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests updating job in db with bad institution ID.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testUpdateJobBadID(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int jobID = myTestJobIDs.get(0);
        final int badInstID = -1;

        myScheduleStoreProxy.getJob(jobID).onSuccess(original -> {
            try {
                final Job modified = new Job(badInstID, new URL(UPDATE_URL), original.getSets().orElse(null),
                        original.getScheduleCronExpression(), OffsetDateTime.now());

                myScheduleStoreProxy.updateJob(jobID, modified).onFailure(details -> {
                    final ServiceException error = (ServiceException) details;

                    aContext.verify(() -> {
                        assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                        assertTrue(error.getMessage().contains(String.valueOf(badInstID)));
                        assertTrue(details instanceof HarvestScheduleStoreServiceException);

                        aContext.completeNow();
                    });
                }).onSuccess(result -> {
                    aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_016, badInstID));
                });
            } catch (final MalformedURLException details) {
                aContext.failNow(details);
            }
        }).onFailure(aContext::failNow);
    }
}
