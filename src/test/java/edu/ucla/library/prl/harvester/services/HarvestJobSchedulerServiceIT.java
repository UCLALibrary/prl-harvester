
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.mail.internet.AddressException;

import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;

import com.google.i18n.phonenumbers.NumberParseException;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.JobResult;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.ino.solrs.JavaAsyncSolrClient;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
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
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final ServiceBinder binder = new ServiceBinder(aVertx);
            final Pool dbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);

            myConfig = config;
            myDbConnectionPool = dbConnectionPool;
            mySolrClient = JavaAsyncSolrClient.create(config.getString(Config.SOLR_CORE_URL));

            // Set up HarvestScheduleStoreService
            final HarvestScheduleStoreService dbService = HarvestScheduleStoreService.create(dbConnectionPool);

            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, dbService);
            myHarvestScheduleStoreServiceProxy = HarvestScheduleStoreService.createProxy(aVertx);

            // Set up HarvestService
            final HarvestService harvestService = HarvestService.create(aVertx, config);

            myHarvestService = binder.setAddress(HarvestService.ADDRESS).register(HarvestService.class, harvestService);

            try {
                myTestProviderBaseURL = new URL(config.getString(Config.TEST_PROVIDER_BASE_URL));
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
            } else {
                return Future.succeededFuture();
            }
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
    @Timeout(value = 90, timeUnit = TimeUnit.SECONDS)
    public void testInstantiationExistingJob(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint serviceSaved = aContext.checkpoint();

        // Add jobs before instantiation of the service
        initDB().compose(numberOfJobsCreated -> {
            final Checkpoint jobResultReceived = aContext.checkpoint(numberOfJobsCreated);
            final EventBus eb = aVertx.eventBus();

            LOGGER.debug("Database initialized");

            eb.<JsonObject>consumer(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, message -> {
                final JobResult jobResult = new JobResult(message.body());
                final CompositeFuture queryBackingServices =
                        CompositeFuture.all(myHarvestScheduleStoreServiceProxy.getJob(jobResult.getJobID()),
                                TestUtils.getAllDocuments(mySolrClient));

                queryBackingServices.onSuccess(results -> {
                    final Job job = results.resultAt(0);
                    final SolrDocumentList solrDocs = results.resultAt(1);

                    aContext.verify(() -> {
                        assertEquals(jobResult.getRecordCount(), solrDocs.getNumFound());
                        assertTrue(job.getLastSuccessfulRun().isPresent());
                        assertEquals(jobResult.getStartTime().withNano(0).toInstant(),
                                job.getLastSuccessfulRun().get().withNano(0).toInstant());

                        jobResultReceived.flag();
                    });
                });
            });

            eb.<String>consumer(HarvestJobSchedulerService.ERROR_ADDRESS, message -> {
                aContext.failNow(message.body());
            });

            // Instantiate the service after jobs have been added to the database
            return HarvestJobSchedulerService.create(aVertx, myConfig).onSuccess(service -> {
                LOGGER.debug("Service instantiated");

                myService = service;

                serviceSaved.flag();

                LOGGER.info("This test may take over a minute to complete, please wait...");
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Gets a Cron expression that will match some time in the future.
     *
     * @param aMinutesLater The number of minutes in the future to create an hourly Cron expression for
     * @return The Cron expression
     * @throws ParseException
     */
    private static CronExpression getFutureCronExpression(final int aMinutesLater) throws ParseException {
        final OffsetDateTime futureTime = OffsetDateTime.now().plusMinutes(aMinutesLater);
        final CronScheduleBuilder scheduleBuilder =
                CronScheduleBuilder.dailyAtHourAndMinute(futureTime.getHour(), futureTime.getMinute());
        final CronTrigger trigger = TriggerBuilder.newTrigger().withSchedule(scheduleBuilder).build();

        LOGGER.debug("Cron expression that will trigger an event in {} minute(s) or less: {}", aMinutesLater,
                trigger.getCronExpression());

        return new CronExpression(trigger.getCronExpression());
    }

    /**
     * @return A Future that resolves to the number of jobs added to the database
     */
    private Future<Integer> initDB() {
        return addInstitution().compose(institutionID -> {
            @SuppressWarnings("rawtypes")
            final Function<Job, Future> addJob = aJob -> (Future) myHarvestScheduleStoreServiceProxy.addJob(aJob);
            final List<Job> jobs = new LinkedList<>();
            final Job job;

            LOGGER.debug("Institution ID: {}", institutionID);

            try {
                job = new Job(institutionID, myTestProviderBaseURL, null, getFutureCronExpression(1), null);
            } catch (final ParseException details) {
                return Future.failedFuture(details);
            }

            jobs.add(job);

            return CompositeFuture.all(jobs.stream().map(addJob).toList()).map(jobs.size());
        });
    }

    private Future<Integer> addInstitution() {
        final Institution institution;

        try {
            institution = TestUtils.getRandomInstitution();
        } catch (AddressException | MalformedURLException | NumberParseException details) {
            return Future.failedFuture(details);
        }

        return myHarvestScheduleStoreServiceProxy.addInstitution(institution);
    }
}
