
package edu.ucla.library.prl.harvester;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.quartz.CronExpression;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Represents an OAI-PMH harvest job.
 */
@DataObject
@SuppressWarnings("PMD.DataClass")
public final class Job {

    /**
     * The JSON key for the institution ID.
     */
    static final String INSTITUTION_ID = "institutionId";

    /**
     * The JSON key for the repository base URL.
     */
    static final String REPOSITORY_BASE_URL = "repositoryBaseUrl";

    /**
     * JSON key for the metadata prefix.
     */
    static final String METADATA_PREFIX = "metadataPrefix";

    /**
     * JSON key for the list of sets.
     */
    static final String SETS = "sets";

    /**
     * JSON key for the schedule.
     */
    static final String SCHEDULE_CRON_EXPRESSION = "scheduleCronExpression";

    /**
     * JSON key for the last successful run.
     */
    static final String LAST_SUCCESSFUL_RUN = "lastSuccessfulRun";

    /**
     * The identifier of the institution that this job should be associated with.
     */
    private final int myInstitutionId;

    /**
     * The base URL of the OAI-PMH repository.
     */
    private final URL myRepositoryBaseUrl;

    /**
     * The list of sets to harvest.
     */
    private final Optional<List<String>> mySets;

    /**
     * The schedule on which this job should be run.
     */
    private final CronExpression myScheduleCronExpression;

    /**
     * The timestamp of the last successful run of this job; will be empty at first.
     */
    private final Optional<ZonedDateTime> myLastSuccessfulRun;

    /**
     * Instantiates a job.
     *
     * @param anInstitutionId The identifier of the institution that this job should be associated with
     * @param aRepositoryBaseUrl The base URL of the OAI-PMH repository
     * @param aSets The list of sets to harvest; if empty, assume all sets should be harvested
     * @param aScheduleCronExpression The schedule on which this job should be run
     * @param aLastSuccessfulRun The timestamp of the last successful run of this job; will be null at first
     */
    public Job(final int anInstitutionId, final URL aRepositoryBaseUrl, final List<String> aSets,
            final CronExpression aScheduleCronExpression, final ZonedDateTime aLastSuccessfulRun) {
        myInstitutionId = anInstitutionId;
        myRepositoryBaseUrl = Objects.requireNonNull(aRepositoryBaseUrl);
        mySets = Optional.ofNullable(aSets);
        myScheduleCronExpression = Objects.requireNonNull(aScheduleCronExpression);
        myLastSuccessfulRun = Optional.ofNullable(aLastSuccessfulRun);
    }

    /**
     * Instantiates a job from its JSON representation.
     * <p>
     * <b>This constructor is meant to be used only by generated service proxy code!</b>
     * {@link #Job(int, URL, List, CronExpression, ZonedDateTime)} should be used everywhere else.
     *
     * @param aJsonObject A job represented as JSON
     * @throws IllegalArgumentException If the JSON representation is invalid
     */
    @SuppressWarnings({ "PMD.AvoidCatchingNPE", "PMD.AvoidCatchingGenericException" })
    public Job(final JsonObject aJsonObject) {
        Objects.requireNonNull(aJsonObject);
        try {
            myInstitutionId = Objects.requireNonNull(aJsonObject.getInteger(INSTITUTION_ID));
            myRepositoryBaseUrl = new URL(Objects.requireNonNull(aJsonObject.getString(REPOSITORY_BASE_URL)));
            mySets = Optional.ofNullable(aJsonObject.getJsonArray(SETS)).map(JsonArray::getList);
            myScheduleCronExpression =
                    new CronExpression(Objects.requireNonNull(aJsonObject.getString(SCHEDULE_CRON_EXPRESSION)));
            myLastSuccessfulRun = Optional.ofNullable(aJsonObject.getString(LAST_SUCCESSFUL_RUN))
                    .map(datetime -> ZonedDateTime.parse(datetime));
        } catch (final DateTimeParseException | MalformedURLException | NullPointerException | ParseException details) {
            // Catch-all because generated event bus proxy code doesn't appreciate checked exceptions
            throw new IllegalArgumentException(details.getMessage(), details);
        }
    }

    /**
     * @return The JSON representation of the job
     */
    public JsonObject toJson() {
        return new JsonObject() //
                .put(INSTITUTION_ID, getInstitutionId()) //
                .put(REPOSITORY_BASE_URL, getRepositoryBaseUrl().toString()).put(METADATA_PREFIX, getMetadataPrefix())//
                .put(SETS, getSets().orElse(null)) //
                .put(SCHEDULE_CRON_EXPRESSION, getScheduleCronExpression().getCronExpression()) //
                .put(LAST_SUCCESSFUL_RUN, getLastSuccessfulRun().map(ZonedDateTime::toString).orElse(null));
    }

    /**
     * @return The institution ID
     */
    public int getInstitutionId() {
        return myInstitutionId;
    }

    /**
     * @return The repository base URL
     */
    public URL getRepositoryBaseUrl() {
        return myRepositoryBaseUrl;
    }

    /**
     * @return The metadata prefix
     */
    public String getMetadataPrefix() {
        return Constants.OAI_DC;
    }

    /**
     * @return The optional list of sets
     */
    public Optional<List<String>> getSets() {
        return mySets;
    }

    /**
     * @return The schedule
     */
    public CronExpression getScheduleCronExpression() {
        return myScheduleCronExpression;
    }

    /**
     * @return The optional last successful run
     */
    public Optional<ZonedDateTime> getLastSuccessfulRun() {
        return myLastSuccessfulRun;
    }
}
