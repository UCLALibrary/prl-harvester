
package edu.ucla.library.prl.harvester.services;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import com.diabolicallabs.vertx.cron.CronEventSchedulerVertical;

import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.JobResult;
import edu.ucla.library.prl.harvester.MessageCodes;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Promise;

/**
 * The implementation of {@link HarvestJobSchedulerService}.
 */
@SuppressWarnings("PMD.UnusedFormalParameter")
public final class HarvestJobSchedulerServiceImpl implements HarvestJobSchedulerService {

    /**
     * The scheduler service's logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestJobSchedulerService.class, MessageCodes.BUNDLE);

    /**
     * The message key for storing the job.
     */
    private static final String JOB = "job";

    /**
     * A message key for storing a job result;
     */
    private static final String JOB_RESULT = "jobResult";

    /**
     * A template string for message consumer addresses.
     */
    private static final String HANDLER_ADDRESS_TEMPLATE = "{}.{}";

    /**
     * The event bus address where {@link myJobTriggerMessageConsumer} will listen for jobs.
     */
    private static final String JOB_HANDLER_ADDRESS =
            StringUtils.format(HANDLER_ADDRESS_TEMPLATE, ADDRESS, JobEventHandler.class.getName());

    /**
     * The event bus address where {@link myJobResultTriggerMessageConsumer} will listen for job results.
     */
    private static final String JOB_RESULT_HANDLER_ADDRESS =
            StringUtils.format(HANDLER_ADDRESS_TEMPLATE, ADDRESS, JobResultEventHandler.class.getName());

    /**
     * The default address for creating a scheduled event, hard-coded in
     * {@link CronEventSchedulerVertical#start(Future)}.
     */
    private static final String VERTX_CRON_CREATE_ADDRESS = "cron.schedule";

    /**
     * The default address for canceling a scheduled event, hard-coded in
     * {@link CronEventSchedulerVertical#start(Future)}.
     */
    private static final String VERTX_CRON_CANCEL_ADDRESS = "cron.cancel";

    /**
     * A Vert.x instance.
     */
    private final Vertx myVertx;

    /**
     * The deployment ID of the scheduler verticle
     */
    private String mySchedulerVerticleDeploymentID;

    /**
     * A message consumer that runs jobs in response to events from the scheduler verticle.
     */
    private MessageConsumer<JsonObject> myJobTriggerMessageConsumer;

    /**
     * A message consumer that updates jobs in response to events from the scheduler verticle.
     */
    private MessageConsumer<JsonObject> myJobResultTriggerMessageConsumer;

    /**
     * Instantiates the service. Call {@link #create(Vertx, JsonObject)} instead of this constructor.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @param anInitialJobsList A list of initial jobs that each have IDs
     * @param aHandler A Handler that is called with the AsyncResult of instantiation
     */
    private HarvestJobSchedulerServiceImpl(final Vertx aVertx, final JsonObject aConfig,
            final List<Job> anInitialJobsList, final Handler<AsyncResult<HarvestJobSchedulerServiceImpl>> aHandler) {
        final Future<String> schedulerVerticleDeployment;
        final Future<Void> instantiation;

        myVertx = aVertx;

        schedulerVerticleDeployment = aVertx.deployVerticle(CronEventSchedulerVertical.class, new DeploymentOptions());
        instantiation = schedulerVerticleDeployment.compose(deploymentID -> {
            @SuppressWarnings("rawtypes")
            final List<Future> addInitialJobs;

            mySchedulerVerticleDeploymentID = deploymentID;
            // Register message consumers at the addresses where the scheduler sends messages and replies, respectively
            // The associated handlers know what to do with a job and a job result, respectively
            myJobTriggerMessageConsumer =
                    aVertx.eventBus().consumer(JOB_HANDLER_ADDRESS, new JobEventHandler(aVertx, aConfig));
            myJobResultTriggerMessageConsumer =
                    aVertx.eventBus().consumer(JOB_RESULT_HANDLER_ADDRESS, new JobResultEventHandler(aVertx));

            try {
                addInitialJobs = anInitialJobsList.parallelStream().map(this::addJob).collect(Collectors.toList());
            } catch (final NoSuchElementException details) {
                return Future.failedFuture(details);
            }

            return CompositeFuture.all(addInitialJobs);
        }).mapEmpty();

        aHandler.handle(instantiation.map(this));
    }

    /**
     * Asynchronously instantiates the service.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @param anInitialJobsList A list of initial jobs that each have IDs
     * @return A Future that resolves to the service if it could be instantiated
     */
    public static Future<HarvestJobSchedulerService> create(final Vertx aVertx, final JsonObject aConfig,
            final List<Job> anInitialJobsList) {
        final Promise<HarvestJobSchedulerService> promise = Promise.promise();

        new HarvestJobSchedulerServiceImpl(aVertx, aConfig, anInitialJobsList, result -> {
            if (result.succeeded()) {
                promise.complete(result.result());
            } else {
                promise.fail(result.cause());
            }
        });

        return promise.future();
    }

    @Override
    public Future<Void> addJob(final Job aJob) {
        // See https://github.com/diabolicallabs/vertx-cron#schedule-an-event for the message schema
        final JsonObject message = new JsonObject() //
                .put("cron_id", aJob.getID().orElseThrow()) //
                .put("cron_expression", aJob.getScheduleCronExpression().getCronExpression()) //
                .put("address", JOB_HANDLER_ADDRESS) //
                .put("message", aJob.toJson()) //
                .put("result_address", JOB_RESULT_HANDLER_ADDRESS);

        return myVertx.eventBus().<String>request(VERTX_CRON_CREATE_ADDRESS, message).recover(details -> {
            return Future.failedFuture(details); // FIXME: add more info
        }).mapEmpty();
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        return removeJob(aJobId).compose(unused -> addJob(aJob)).mapEmpty();
    }

    @Override
    public Future<Void> removeJob(final int aJobId) {
        return myVertx.eventBus().<Void>request(VERTX_CRON_CANCEL_ADDRESS, String.valueOf(aJobId)).mapEmpty();
    }

    @Override
    public Future<Void> close() {
        return myVertx.undeploy(mySchedulerVerticleDeploymentID).compose(unused -> {
            // Now that we're sure the scheduler verticle can no longer send events, unregister its message consumers
            return CompositeFuture
                    .all(myJobTriggerMessageConsumer.unregister(), myJobResultTriggerMessageConsumer.unregister())
                    .mapEmpty();
        });
    }

    /**
     * A handler for job events.
     */
    private final class JobEventHandler implements Handler<Message<JsonObject>> {

        /**
         * A proxy to the harvest service, for running jobs.
         */
        private final HarvestService myHarvestService;

        /**
         * @param aVertx A Vert.x instance
         * @param aConfig A configuration
         */
        JobEventHandler(final Vertx aVertx, final JsonObject aConfig) {
            myHarvestService = HarvestService.createProxy(aVertx, aConfig);
        }

        /**
         * Handles a message (a harvest job) sent by the scheduler verticle.
         *
         * @param aMessage A message containing a job as JSON
         */
        @Override
        public void handle(final Message<JsonObject> aMessage) {
            final Job job = new Job(aMessage.body());

            myHarvestService.run(job).onSuccess(result -> {
                final JsonObject replyMessage = new JsonObject() //
                        .put(JOB, job.toJson()) //
                        .put(JOB_RESULT, result.toJson());

                aMessage.reply(replyMessage);
            }).onFailure(details -> aMessage.fail(69, details.getMessage())); // FIXME
        }
    }

    /**
     * A handler for job result events.
     */
    private final class JobResultEventHandler implements Handler<Message<JsonObject>> {

        /**
         * A proxy to the harvest schedule store service, for retrieving and updating jobs (only in the event of a
         * successful job).
         */
        private final HarvestScheduleStoreService myHarvestScheduleStoreService;

        /**
         * @param aVertx A Vert.x instance
         */
        JobResultEventHandler(final Vertx aVertx) {
            myHarvestScheduleStoreService = HarvestScheduleStoreService.createProxy(aVertx);
        }

        /**
         * Handles the reply (a harvest job result) of a message sent by the scheduler verticle.
         * <p>
         * The format of this reply is defined in {@link JobEventHandler#handle(Message)}.
         *
         * @param aMessage A message containing a job and a job result as JSON
         * @throws NoSuchElementException If the job represented by the message does not have an ID
         */
        @Override
        public void handle(final Message<JsonObject> aMessage) {
            final Job job = new Job(aMessage.body().getJsonObject(JOB));
            final JobResult jobResult = new JobResult(aMessage.body().getJsonObject(JOB_RESULT));
            final int jobID = job.getID().orElseThrow();
            final Job newJob = new Job( //
                    job.getInstitutionID(), //
                    job.getRepositoryBaseURL(), //
                    job.getSets().orElse(null), //
                    job.getScheduleCronExpression(), //
                    jobResult.getStartTime());

            myHarvestScheduleStoreService.updateJob(jobID, newJob).compose(unused -> updateJob(jobID, newJob))
                    .onFailure(details -> {
                        LOGGER.error("Failed to update job: _");
                    });
        }
    }
}
