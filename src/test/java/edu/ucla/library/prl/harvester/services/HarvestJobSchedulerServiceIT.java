
package edu.ucla.library.prl.harvester.services;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.JobResult;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.services.HarvestJobSchedulerServiceImpl.RunHarvest;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.core.Promise;
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

    private MessageConsumer<JsonObject> myHarvestScheduleStoreService;

    private HarvestScheduleStoreService myHarvestScheduleStoreServiceProxy;

    private MessageConsumer<JsonObject> myHarvestService;

    private HarvestService myHarvestServiceProxy;

    private Pool myDbConnectionPool;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final Pool dbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            final HarvestScheduleStoreService scheduleStoreService =
                    HarvestScheduleStoreService.create(dbConnectionPool);
            final HarvestService harvestService = HarvestService.create(aVertx, config);
            // final HarvestJobSchedulerService svc = HarvestJobSchedulerService.create(aVertx, config);
            final ServiceBinder binder = new ServiceBinder(aVertx);

            myConfig = config;

            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, scheduleStoreService);
            myHarvestScheduleStoreServiceProxy = HarvestScheduleStoreService.createProxy(aVertx);

            myHarvestService = binder.setAddress(HarvestService.ADDRESS).register(HarvestService.class, harvestService);

            // myHarvestJobSchedulerService =
            // binder.setAddress(HarvestJobSchedulerService.ADDRESS).register(HarvestJobSchedulerService.class, svc);
            // myHarvestJobSchedulerServiceProxy = HarvestJobSchedulerService.createProxy(aVertx);

            myDbConnectionPool = dbConnectionPool;

            return Future.succeededFuture();
        }).onSuccess(nil -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeEach
    public void beforeEach(final Vertx aVertx, final VertxTestContext aContext) {
        TestUtils.wipeDatabase(myDbConnectionPool).onSuccess(result -> aContext.completeNow())
                .onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
    }

    /**
     * Tests that a new service instance triggers the near-future job(s) that it is initially provided.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @throws ParseException
     * @throws MalformedURLException
     */
    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.MINUTES)
    public void testThat(final Vertx aVertx, final VertxTestContext aContext)
            throws MalformedURLException, ParseException {
        final List<Job> jobs;
        final Future<HarvestJobSchedulerService> instantiation;

        final Promise<Void> promise = Promise.promise();

        aVertx.eventBus().<JsonObject>consumer(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, message -> {
            LOGGER.info("Job result received by {}: {}", this.getClass().getSimpleName(), message.body().encode());

            promise.complete();
        });

        jobs = List.of( //
                Job.withID(new Job(1, new URL("http://example.edu/oai"), null, getFutureCronExpression(1), null), 42));
        instantiation = HarvestJobSchedulerServiceImpl.create(aVertx, myConfig, jobs, FakeHarvest.class);

        instantiation.compose(service -> {
            return promise.future().compose(result -> service.close());
        }).onSuccess(result -> {
            LOGGER.debug("For some reason, this onSuccess handler is being invoked, but the test still times out.");

            aContext.completeNow();
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

        LOGGER.debug("Cron expression that will trigger an event in less than {} minute(s): {}", aMinutesLater,
                trigger.getCronExpression());

        return new CronExpression(trigger.getCronExpression());
    }

    /**
     * This implementation is almost exactly the same as {@link RunHarvest}, except that it doesn't actually call a
     * {@link HarvestService}, and it has additional debug logging.
     */
    public static final class FakeHarvest implements org.quartz.Job {

        public FakeHarvest() {
        }

        @Override
        public void execute(final JobExecutionContext aContext) throws JobExecutionException {
            try {
                final SchedulerContext schedulerContext = aContext.getScheduler().getContext();
                final Context vertxContext = (Context) schedulerContext.get(HarvestJobSchedulerService.VERTX_CONTEXT);

                LOGGER.debug("Vert.x context identifier: {}", vertxContext);

                vertxContext.runOnContext(nil -> {
                    final String encodedJobJSON = aContext.getJobDetail().getJobDataMap()
                            .getString(HarvestJobSchedulerService.ENCODED_JOB_JSON);
                    final Job job = new Job(new JsonObject(encodedJobJSON));
                    final EventBus eb = vertxContext.owner().eventBus();

                    LOGGER.debug(StringUtils.format("Job execution triggered at {}: {}", OffsetDateTime.now(),
                            job.toJson().encode()));

                    Future.succeededFuture(new JobResult(OffsetDateTime.now(), 0)).onSuccess(jobResult -> {
                        eb.publish(HarvestJobSchedulerService.JOB_RESULT_ADDRESS, jobResult.toJson());
                    }).onFailure(details -> {
                        eb.publish(HarvestJobSchedulerService.ERROR_ADDRESS, details);
                    });
                });
            } catch (final SchedulerException details) {
                LOGGER.error(details.getMessage());
            }
        }
    }
}
