
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.mail.internet.AddressException;

import org.apache.solr.common.SolrDocumentList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.i18n.phonenumbers.NumberParseException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.JobResult;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.Pool;

/**
 * Tests {@link HarvestJobSchedulerService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
@DisabledIfSystemProperty(named = "skipCronTests", matches = "true")
public class HarvestJobSchedulerServiceIT {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HarvestJobSchedulerServiceIT.class, MessageCodes.BUNDLE);

    private JsonObject myConfig;

    private Pool myDbConnectionPool;

    private JavaAsyncSolrClient mySolrClient;

    private HarvestJobSchedulerService myService;

    private HarvestScheduleStoreService myHarvestScheduleStoreServiceProxy;

    private MessageConsumer<JsonObject> myHarvestService;

    private MessageConsumer<JsonObject> myHarvestScheduleStoreService;

    private URL myTestProviderBaseURL;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        Config.getConfig(aVertx).compose(config -> {
            final ServiceBinder binder = new ServiceBinder(aVertx);
            final Pool dbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);

            myConfig = config;
            myDbConnectionPool = dbConnectionPool;
            mySolrClient = JavaAsyncSolrClient.create(config.getString(Config.SOLR_CORE_URL));

            // Set up HarvestScheduleStoreService
            final HarvestScheduleStoreService dbService = HarvestScheduleStoreService.create(aVertx, dbConnectionPool);

            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, dbService);
            myHarvestScheduleStoreServiceProxy = HarvestScheduleStoreService.createProxy(aVertx);

            // Set up HarvestService
            final HarvestService harvestService = HarvestService.create(aVertx, config);

            myHarvestService = binder.setAddress(HarvestService.ADDRESS).register(HarvestService.class, harvestService);

            try {
                myTestProviderBaseURL = new URL(config.getString(TestUtils.TEST_PROVIDER_BASE_URL));
            } catch (final MalformedURLException details) {
                return Future.failedFuture(details);
            }

            return Future.succeededFuture();
        }).onSuccess(nil -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterEach
    public void afterEach(final Vertx aVertx, final VertxTestContext aContext) {
        final CompositeFuture wipeBackingServices =
                CompositeFuture.all(TestUtils.wipeDatabase(myDbConnectionPool), TestUtils.wipeSolr(mySolrClient));

        wipeBackingServices.compose(nil -> {
            if (myService != null) {
                return myService.close();
            }

            return Future.succeededFuture();
        }).onSuccess(nil -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        myHarvestScheduleStoreServiceProxy.close().compose(nil -> {
            mySolrClient.shutdown();

            return myDbConnectionPool.close();
        }).onSuccess(nil -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Tests that a service instance triggers the near-future job(s) that were already in the database before
     * instantiation.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    public void testInstantiationExistingJob(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint serviceSaved = aContext.checkpoint();
        final List<String> bothSets = List.of(TestUtils.SET1, TestUtils.SET2);

        // Add jobs before instantiation of the service
        addInstitution().compose(institutionID -> addJob(institutionID, bothSets)).compose(jobID -> {
            final Checkpoint jobResultReceived = aContext.checkpoint();

            LOGGER.debug(MessageCodes.PRL_030);

            aVertx.eventBus().<JsonObject>consumer(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, message -> {
                // The application code that updates the database is listening on this address too, so wait a second to
                // ensure that happened
                aVertx.setTimer(1000, timerID -> {
                    final JobResult jobResult = new JobResult(message.body());
                    final CompositeFuture queryBackingServices =
                            CompositeFuture.all(myHarvestScheduleStoreServiceProxy.getJob(jobResult.getJobID()),
                                    TestUtils.getItemRecordDocuments(mySolrClient));

                    queryBackingServices.onSuccess(results -> {
                        final Job job = results.resultAt(0);
                        final SolrDocumentList solrDocs = results.resultAt(1);

                        aContext.verify(() -> {
                            assertEquals(TestUtils.SET1_RECORD_COUNT + TestUtils.SET2_RECORD_COUNT,
                                    jobResult.getRecordCount());
                            assertEquals(jobResult.getRecordCount(), solrDocs.getNumFound());
                            assertEquals(0, jobResult.getDeletedRecordCount());
                            assertTrue(job.getLastSuccessfulRun().isPresent());
                            assertEquals(jobResult.getStartTime().withNano(0).toInstant(),
                                    job.getLastSuccessfulRun().get().withNano(0).toInstant());

                            jobResultReceived.flag();
                        });
                    });
                });
            });

            aVertx.eventBus().<String>consumer(HarvestJobSchedulerService.ERROR_ADDRESS, message -> {
                aContext.failNow(message.body());
            });

            // Instantiate the service after jobs have been added to the database
            return HarvestJobSchedulerService.create(aVertx, myConfig).onSuccess(service -> {
                LOGGER.debug(MessageCodes.PRL_031);

                myService = service;

                serviceSaved.flag();

                LOGGER.info(MessageCodes.PRL_032);
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that a service instance triggers the near-future job(s) that were added after instantiation.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    public void testAddJobsAfterInstantiation(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint serviceSavedAndDbInitialized = aContext.checkpoint();

        HarvestJobSchedulerService.create(aVertx, myConfig).compose(service -> {
            final Checkpoint jobResultReceived = aContext.checkpoint();

            LOGGER.debug(MessageCodes.PRL_031);

            aVertx.eventBus().<JsonObject>consumer(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, message -> {
                // The application code that updates the database is listening on this address too, so wait a second to
                // ensure that happened
                aVertx.setTimer(1000, timerID -> {
                    final JobResult jobResult = new JobResult(message.body());
                    final CompositeFuture queryBackingServices =
                            CompositeFuture.all(myHarvestScheduleStoreServiceProxy.getJob(jobResult.getJobID()),
                                    TestUtils.getItemRecordDocuments(mySolrClient));

                    queryBackingServices.onSuccess(results -> {
                        final Job job = results.resultAt(0);
                        final SolrDocumentList solrDocs = results.resultAt(1);

                        aContext.verify(() -> {
                            assertEquals(TestUtils.SET1_RECORD_COUNT, jobResult.getRecordCount());
                            assertEquals(jobResult.getRecordCount(), solrDocs.getNumFound());
                            assertEquals(0, jobResult.getDeletedRecordCount());
                            assertTrue(job.getLastSuccessfulRun().isPresent());
                            assertEquals(jobResult.getStartTime().withNano(0).toInstant(),
                                    job.getLastSuccessfulRun().get().withNano(0).toInstant());

                            jobResultReceived.flag();
                        });
                    });
                });
            });

            aVertx.eventBus().<String>consumer(HarvestJobSchedulerService.ERROR_ADDRESS, message -> {
                aContext.failNow(message.body());
            });

            myService = service;

            return addInstitution().compose(institutionID -> {
                final Job job;

                try {
                    job = new Job(institutionID, myTestProviderBaseURL, List.of(TestUtils.SET1),
                            TestUtils.getFutureCronExpression(5), null);
                } catch (final ParseException details) {
                    return Future.failedFuture(details);
                }

                return myHarvestScheduleStoreServiceProxy.addJobs(List.of(job)).compose(jobsWithIDs -> {
                    return service.addJobs(jobsWithIDs);
                });
            });
        }).onSuccess(initialJob -> {
            LOGGER.debug(MessageCodes.PRL_030);
            LOGGER.info(MessageCodes.PRL_032);

            serviceSavedAndDbInitialized.flag();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that a job can be updated.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    public void testUpdateJob(final Vertx aVertx, final VertxTestContext aContext) {
        // Make sure the job updates later, and uses a different record count
        final Checkpoint serviceSaved = aContext.checkpoint();

        // Add jobs before instantiation of the service
        addInstitution().compose(institutionID -> addJob(institutionID, List.of(TestUtils.SET2))).compose(jobID -> {
            final Checkpoint jobResultReceived = aContext.checkpoint(1);

            LOGGER.debug(MessageCodes.PRL_030);

            aVertx.eventBus().<JsonObject>consumer(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, message -> {
                // The application code that updates the database is listening on this address too, so wait a second to
                // ensure that happened
                aVertx.setTimer(1000, timerID -> {
                    final JobResult jobResult = new JobResult(message.body());
                    final CompositeFuture queryBackingServices =
                            CompositeFuture.all(myHarvestScheduleStoreServiceProxy.getJob(jobResult.getJobID()),
                                    TestUtils.getItemRecordDocuments(mySolrClient));

                    queryBackingServices.onSuccess(results -> {
                        final Job job = results.resultAt(0);
                        final SolrDocumentList solrDocs = results.resultAt(1);

                        aContext.verify(() -> {
                            assertEquals(TestUtils.SET2_RECORD_COUNT, jobResult.getRecordCount());
                            assertEquals(jobResult.getRecordCount(), solrDocs.getNumFound());
                            assertEquals(0, jobResult.getDeletedRecordCount());
                            assertTrue(job.getLastSuccessfulRun().isPresent());
                            assertEquals(jobResult.getStartTime().withNano(0).toInstant(),
                                    job.getLastSuccessfulRun().get().withNano(0).toInstant());

                            jobResultReceived.flag();
                        });
                    });
                });
            });

            aVertx.eventBus().<String>consumer(HarvestJobSchedulerService.ERROR_ADDRESS, message -> {
                aContext.failNow(message.body());
            });

            // Instantiate the service after jobs have been added to the database
            return HarvestJobSchedulerService.create(aVertx, myConfig).compose(service -> {
                LOGGER.debug(MessageCodes.PRL_031);

                myService = service;

                serviceSaved.flag();

                return updateJob(jobID, List.of(TestUtils.SET2));
            }).onSuccess(result -> {
                LOGGER.info(MessageCodes.PRL_032);
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that a removed job does not execute.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    public void testRemoveJob(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint serviceSaved = aContext.checkpoint();
        final Checkpoint removedJobDidNotExecute = aContext.checkpoint();

        // Add jobs before instantiation of the service
        addInstitution().compose(institutionID -> addJob(institutionID, List.of())).compose(jobID -> {
            final OffsetDateTime now = OffsetDateTime.now().withNano(0);
            // After this time, we can be reasonably certain that the canceled job won't run
            final OffsetDateTime whenWeWillKnow = now.plusSeconds(10);
            final Duration reasonablySufficientWait = Duration.between(now, whenWeWillKnow);

            LOGGER.debug(MessageCodes.PRL_034, whenWeWillKnow);
            LOGGER.debug(MessageCodes.PRL_030);

            aVertx.setTimer(reasonablySufficientWait.toMillis(), timerID -> {
                removedJobDidNotExecute.flag();
            });

            aVertx.eventBus().<JsonObject>consumer(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, message -> {
                aContext.failNow(MessageCodes.PRL_035);
            });

            aVertx.eventBus().<String>consumer(HarvestJobSchedulerService.ERROR_ADDRESS, message -> {
                aContext.failNow(message.body());
            });

            // Instantiate the service after jobs have been added to the database
            return HarvestJobSchedulerService.create(aVertx, myConfig).compose(service -> {
                LOGGER.debug(MessageCodes.PRL_031);

                myService = service;

                serviceSaved.flag();

                return service.removeJob(jobID);
            }).onSuccess(nil -> {
                LOGGER.info(MessageCodes.PRL_032);
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * @return A Future that succeeds if a random institution was added to the database
     */
    private Future<Integer> addInstitution() {
        final Institution institution;

        try {
            institution = TestUtils.getRandomInstitution();
        } catch (AddressException | MalformedURLException | NumberParseException details) {
            return Future.failedFuture(details);
        }

        return myHarvestScheduleStoreServiceProxy.addInstitutions(List.of(institution))
                .map(institutions -> institutions.get(0).getID().get());
    }

    /**
     * @param anInstitutionID The institution ID to associate with a new job
     * @param aSets The OAI-PMH sets to harvest
     * @return A Future that succeeds if a job with these parameters was added to the database
     */
    private Future<Integer> addJob(final int anInstitutionID, final List<String> aSets) {
        final Job job;

        try {
            job = new Job(anInstitutionID, myTestProviderBaseURL, aSets, TestUtils.getFutureCronExpression(5), null);
        } catch (final ParseException details) {
            return Future.failedFuture(details);
        }

        return myHarvestScheduleStoreServiceProxy.addJobs(List.of(job)).map(jobs -> jobs.get(0).getID().get());
    }

    /**
     * @param aJobID The ID of the job to update
     * @param aSets The new sets to use
     * @return A Future that succeeds if the job was updated in the database
     */
    private Future<Void> updateJob(final int aJobID, final List<String> aSets) {
        return myHarvestScheduleStoreServiceProxy.getJob(aJobID).compose(job -> {
            final Job updatedJob = new Job(job.getInstitutionID(), job.getRepositoryBaseURL(), aSets,
                    job.getScheduleCronExpression(), null);

            return myHarvestScheduleStoreServiceProxy.updateJob(aJobID, updatedJob);
        });
    }
}
