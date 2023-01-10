
package edu.ucla.library.prl.harvester.services;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

/**
 * The implementation of {@link HarvestJobSchedulerService}.
 */
public final class HarvestJobSchedulerServiceImpl implements HarvestJobSchedulerService {

    /**
     * The scheduler service's logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestJobSchedulerService.class, MessageCodes.BUNDLE);

    /**
     * The {@link SchedulerContext} key for the harvest service proxy.
     */
    private static final String HARVEST_SERVICE = "harvestService";

    /**
     * A proxy to the harvest service, for running jobs.
     */
    private final HarvestService myHarvestService;

    /**
     * A job scheduler.
     */
    private final Scheduler myScheduler;

    /**
     * The type of each action to schedule.
     */
    private final Class<? extends org.quartz.Job> myJobClass;

    /**
     * Instantiates the service. Call {@link HarvestJobSchedulerService#create} instead of this constructor.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @param anInitialJobsList A list of initial jobs, each with IDs assigned by the database
     * @param aJobClass The action to execute on each trigger
     * @param aHandler A Handler that is called with the AsyncResult of instantiation
     * @throws SchedulerException
     */
    protected HarvestJobSchedulerServiceImpl(final Vertx aVertx, final JsonObject aConfig,
            final List<Job> anInitialJobsList, final Class<? extends org.quartz.Job> aJobClass,
            final Handler<AsyncResult<HarvestJobSchedulerService>> aHandler) throws SchedulerException {
        final Future<Void> instantiation;
        @SuppressWarnings("rawtypes")
        final List<Future> addInitialJobs;

        myHarvestService = HarvestService.createProxy(aVertx, aConfig);

        myScheduler = new StdSchedulerFactory().getScheduler();
        myScheduler.getContext().put(VERTX_CONTEXT, aVertx.getOrCreateContext());
        myScheduler.getContext().put(HARVEST_SERVICE, myHarvestService);
        myScheduler.start();

        myJobClass = aJobClass;

        addInitialJobs = anInitialJobsList.parallelStream().map(this::addJob).collect(Collectors.toList());

        aVertx.eventBus().<JsonObject>consumer(JOB_RESULT_ADDRESS, message -> {
            final Job updatedJob = null; // FIXME

            LOGGER.info("Job result received by {}: {}", this.getClass().getSimpleName(), message.body().encode());

            updateJob("FIXME".length(), updatedJob);
        });

        aVertx.eventBus().<Throwable>consumer(ERROR_ADDRESS, message -> {
            LOGGER.error("Error received by {}: {}", this.getClass().getSimpleName(), message.body().getMessage());
        });

        instantiation = CompositeFuture.all(addInitialJobs).mapEmpty();

        aHandler.handle(instantiation.map((HarvestJobSchedulerService) this));
    }

    /**
     * Asynchronously instantiates the service.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @param anInitialJobsList A list of initial jobs, each with IDs assigned by the database
     * @param aJobClass The action to execute on each trigger
     * @return A Future that resolves to the service if it could be instantiated
     */
    static Future<HarvestJobSchedulerService> create(final Vertx aVertx, final JsonObject aConfig,
            final List<Job> anInitialJobsList, final Class<? extends org.quartz.Job> aJobClass) {
        final Promise<HarvestJobSchedulerService> promise = Promise.promise();

        try {
            new HarvestJobSchedulerServiceImpl(aVertx, aConfig, anInitialJobsList, aJobClass, promise);
        } catch (final SchedulerException details) {
            promise.fail(details);
        }

        return promise.future();
    }

    @Override
    public Future<Void> addJob(final Job aJob) {
        final JobKey key = new JobKey(Integer.toString(aJob.getID().get())); // FIXME: risk of NoSuchElementException
        final JobDetail jobDetail = JobBuilder.newJob(myJobClass).withIdentity(key)
                .usingJobData(ENCODED_JOB_JSON, aJob.toJson().encode()).build();
        final CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(aJob.getScheduleCronExpression());
        final CronTrigger trigger =
                TriggerBuilder.newTrigger().withSchedule(scheduleBuilder).startNow().endAt(null).build();

        try {
            myScheduler.scheduleJob(jobDetail, trigger);

            LOGGER.debug("Next fire time: {}", trigger.getNextFireTime());

            return Future.succeededFuture();
        } catch (final SchedulerException details) {
            return Future.failedFuture(details);
        }
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        return Future.failedFuture("FIXME");
    }

    @Override
    public Future<Void> removeJob(final int aJobId) {
        final JobKey key = new JobKey(Integer.toString(aJobId));

        try {
            if (myScheduler.deleteJob(key)) {
                return Future.succeededFuture();
            } else {
                return Future.failedFuture("FIXME");
            }
        } catch (final SchedulerException details) {
            return Future.failedFuture(details);
        }
    }

    @Override
    public Future<Void> close() {
        try {
            myScheduler.shutdown();

            return Future.succeededFuture();
        } catch (final SchedulerException details) {
            return Future.failedFuture(details);
        }
    }

    /**
     * Runs a harvest job, then publishes its result (or resulting error) to the event bus addresses
     * {@link JOB_RESULT_ADDRESS} and {@link ERROR_ADDRESS}, respectively.
     */
    public static final class RunHarvest implements org.quartz.Job {

        public RunHarvest() {
        }

        @Override
        public void execute(final JobExecutionContext aContext) throws JobExecutionException {
            try {
                final SchedulerContext schedulerContext = aContext.getScheduler().getContext();
                final Context vertxContext = (Context) schedulerContext.get(HarvestJobSchedulerService.VERTX_CONTEXT);

                LOGGER.debug("Vert.x context identifier: {}", vertxContext);

                vertxContext.runOnContext(nil -> {
                    final String encodedJobJSON = aContext.getJobDetail().getJobDataMap().getString(ENCODED_JOB_JSON);
                    final Job job = new Job(new JsonObject(encodedJobJSON));
                    final HarvestService harvestService = (HarvestService) schedulerContext.get(HARVEST_SERVICE);
                    final EventBus eb = vertxContext.owner().eventBus();

                    harvestService.run(job).onSuccess(jobResult -> {
                        eb.publish(JOB_RESULT_ADDRESS, jobResult.toJson());
                    }).onFailure(details -> {
                        eb.publish(ERROR_ADDRESS, details);
                    });
                });
            } catch (final SchedulerException details) {
                LOGGER.error(details.getMessage());
            }
        }
    }
}
