
package edu.ucla.library.prl.harvester.services;

import java.util.Set;

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

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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
     * The {@link JobDataMap} key for the JSON-encoded harvest job.
     */
    private static final String ENCODED_JOB_JSON = "encodedJobJSON";

    /**
     * The {@link SchedulerContext} key for the Vert.x context.
     */
    private static final String VERTX_EVENT_BUS = "vertxEventBus";

    /**
     * The {@link SchedulerContext} key for the harvest service proxy.
     */
    private static final String HARVEST_SERVICE = "harvestService";

    /**
     * The {@link SchedulerContext} key for the harvest schedule store service proxy.
     */
    private static final String HARVEST_SCHEDULE_STORE_SERVICE = "harvestScheduleStoreService";

    /**
     * A proxy to the harvest service, for running jobs.
     */
    @SuppressWarnings("PMD.SingularField")
    private final HarvestService myHarvestService;

    /**
     * A proxy to the harvest schedule store service.
     */
    private final HarvestScheduleStoreService myHarvestScheduleStoreService;

    /**
     * A job scheduler.
     */
    private final Scheduler myScheduler;

    /**
     * Instantiates the service. Call {@link HarvestJobSchedulerService#create} instead of this constructor.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @throws SchedulerException If there is a problem with the underlying scheduler
     */
    protected HarvestJobSchedulerServiceImpl(final Vertx aVertx, final JsonObject aConfig) throws SchedulerException {
        myHarvestService = HarvestService.createProxy(aVertx, aConfig);
        myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);

        myScheduler = new StdSchedulerFactory().getScheduler();
        myScheduler.getContext().put(VERTX_EVENT_BUS, aVertx.eventBus());
        myScheduler.getContext().put(HARVEST_SERVICE, myHarvestService);
        myScheduler.getContext().put(HARVEST_SCHEDULE_STORE_SERVICE, myHarvestScheduleStoreService);
        myScheduler.start();
    }

    @Override
    public Future<Integer> addJob(final Job aJob) {
        return myHarvestScheduleStoreService.addJob(aJob)
                .compose(jobID -> scheduleJob(Job.withID(aJob, jobID), false).map(jobID));
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        return myHarvestScheduleStoreService.updateJob(aJobId, aJob)
                .compose(nil -> scheduleJob(Job.withID(aJob, aJobId), true));
    }

    @Override
    public Future<Void> removeJob(final int aJobId) {
        return myHarvestScheduleStoreService.removeJob(aJobId).compose(nil -> unscheduleJob(aJobId));
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
     * Initializes the scheduler with all of the {@link Job}s stored in the database.
     * <p>
     * If this method has already been called on the instance, calling it again will result in a no-op.
     *
     * @return A Future that succeeds if the saved jobs were restored
     */
    @SuppressWarnings("rawtypes")
    Future<Void> initializeScheduler() {
        return myHarvestScheduleStoreService.listJobs().compose(jobs -> {
            return CompositeFuture.all(jobs.stream().map(job -> (Future) scheduleJob(job, true)).toList());
        }).mapEmpty();
    }

    /**
     * @param aJob A job
     * @param aReplaceIfExists Whether or not to overwrite the job if it already exists
     * @return A Future that succeeds if the job was added to, or updated in, the scheduler
     */
    private Future<Void> scheduleJob(final Job aJob, final boolean aReplaceIfExists) {
        final JobKey key = new JobKey(Integer.toString(aJob.getID().get()));
        final JobDetail jobDetail = JobBuilder.newJob(RunHarvest.class).withIdentity(key)
                .usingJobData(ENCODED_JOB_JSON, aJob.toJson().encode()).build();
        final CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(aJob.getScheduleCronExpression());
        final CronTrigger trigger =
                TriggerBuilder.newTrigger().withSchedule(scheduleBuilder).startNow().endAt(null).build();

        try {
            myScheduler.scheduleJob(jobDetail, Set.of(trigger), aReplaceIfExists);

            return Future.succeededFuture();
        } catch (final SchedulerException details) {
            return Future.failedFuture(LOGGER.getMessage(MessageCodes.PRL_020, key.getName(), details.getMessage()));
        }
    }

    /**
     * @param aJobID A {@link Job} ID
     * @return A Future that succeeds if the job was removed from the scheduler
     */
    private Future<Void> unscheduleJob(final Integer aJobID) {
        final JobKey key = new JobKey(Integer.toString(aJobID));

        try {
            if (myScheduler.deleteJob(key)) {
                return Future.succeededFuture();
            } else {
                return Future.failedFuture(LOGGER.getMessage(MessageCodes.PRL_021, key.getName(), "not found"));
            }
        } catch (final SchedulerException details) {
            return Future.failedFuture(LOGGER.getMessage(MessageCodes.PRL_021, key.getName(), details.getMessage()));
        }
    }

    /**
     * Runs a harvest job, then publishes its result (or resulting error) to the event bus addresses
     * {@link JOB_RESULT_ADDRESS} and {@link ERROR_ADDRESS}, respectively.
     */
    public static final class RunHarvest implements org.quartz.Job {

        /**
         * Per the docs for {@link org.quartz.Job}, a public no-argument constructor is required.
         */
        @SuppressWarnings({ "PMD.UncommentedEmptyConstructor", "PMD.UnnecessaryConstructor" })
        public RunHarvest() {
        }

        @Override
        public void execute(final JobExecutionContext aContext) throws JobExecutionException {
            try {
                final JobDetail jobDetail = aContext.getJobDetail();
                final String encodedJobJSON = jobDetail.getJobDataMap().getString(ENCODED_JOB_JSON);
                final Job job = new Job(new JsonObject(encodedJobJSON));

                final SchedulerContext schedulerContext = aContext.getScheduler().getContext();
                final HarvestService harvestService = (HarvestService) schedulerContext.get(HARVEST_SERVICE);
                final EventBus eventBus = (EventBus) schedulerContext.get(VERTX_EVENT_BUS);

                harvestService.run(job).compose(jobResult -> {
                    final int jobID = Integer.parseInt(jobDetail.getKey().getName());
                    final Job updatedJob = new Job(job.getInstitutionID(), job.getRepositoryBaseURL(),
                            job.getSets().orElse(null), job.getScheduleCronExpression(), jobResult.getStartTime());
                    final HarvestScheduleStoreService scheduleStoreService =
                            (HarvestScheduleStoreService) schedulerContext.get(HARVEST_SCHEDULE_STORE_SERVICE);

                    return scheduleStoreService.updateJob(jobID, updatedJob).onSuccess(result -> {
                        eventBus.publish(JOB_RESULT_ADDRESS, jobResult.toJson());
                    });
                }).onFailure(details -> {
                    eventBus.publish(ERROR_ADDRESS, details.getMessage());
                });
            } catch (final SchedulerException details) {
                LOGGER.error(details.getMessage());
            }
        }
    }
}
