
package edu.ucla.library.prl.harvester;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.quartz.CronExpression;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.templates.SqlTemplate;

/**
 * Represents an OAI-PMH harvest job.
 */
@DataObject
@SuppressWarnings("PMD.DataClass")
public final class Job {

    /**
     * The JSON key for the ID.
     */
    public static final String ID = "id";

    /**
     * The JSON key for the institution ID.
     */
    static final String INSTITUTION_ID = "institutionID";

    /**
     * The JSON key for the repository base URL.
     */
    static final String REPOSITORY_BASE_URL = "repositoryBaseURL";

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
     * The identifier of the job.
     */
    private final Optional<Integer> myID;

    /**
     * The identifier of the institution that this job should be associated with.
     */
    private final int myInstitutionID;

    /**
     * The base URL of the OAI-PMH repository.
     */
    private final URL myRepositoryBaseURL;

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
    private final Optional<OffsetDateTime> myLastSuccessfulRun;

    /**
     * Instantiates a job.
     *
     * @param anInstitutionID The identifier of the institution that this job should be associated with
     * @param aRepositoryBaseURL The base URL of the OAI-PMH repository
     * @param aSets The list of sets to harvest; if empty, assume all sets should be harvested
     * @param aScheduleCronExpression The schedule on which this job should be run
     * @param aLastSuccessfulRun The timestamp of the last successful run of this job; will be null at first
     */
    public Job(final int anInstitutionID, final URL aRepositoryBaseURL, final List<String> aSets,
            final CronExpression aScheduleCronExpression, final OffsetDateTime aLastSuccessfulRun) {
        myID = Optional.empty();
        myInstitutionID = anInstitutionID;
        myRepositoryBaseURL = Objects.requireNonNull(aRepositoryBaseURL);
        mySets = Optional.ofNullable(aSets);
        myScheduleCronExpression = Objects.requireNonNull(aScheduleCronExpression);
        myLastSuccessfulRun = Optional.ofNullable(aLastSuccessfulRun);
    }

    /**
     * Instantiates a job from its JSON representation.
     * <p>
     * Note that the JSON representation may contain an ID, which must have been assigned by the database.
     *
     * @param aJsonObject A job represented as JSON
     * @throws InvalidJobJsonException If the JSON representation is invalid
     */
    @SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity" })
    public Job(final JsonObject aJsonObject) {
        Objects.requireNonNull(aJsonObject);

        final Integer institutionID = aJsonObject.getInteger(INSTITUTION_ID);
        final String repositoryBaseURL = aJsonObject.getString(REPOSITORY_BASE_URL);
        final String scheduleCronExpression = aJsonObject.getString(SCHEDULE_CRON_EXPRESSION);

        myID = Optional.ofNullable(aJsonObject.getInteger(ID));

        if (institutionID != null) {
            myInstitutionID = institutionID;
        } else {
            throw new InvalidJobJsonException(MessageCodes.PRL_002, INSTITUTION_ID);
        }

        if (repositoryBaseURL != null) {
            try {
                myRepositoryBaseURL = new URL(repositoryBaseURL);
            } catch (final MalformedURLException details) {
                throw new InvalidJobJsonException(details, MessageCodes.PRL_004, REPOSITORY_BASE_URL,
                        details.getMessage());
            }
        } else {
            throw new InvalidJobJsonException(MessageCodes.PRL_002, REPOSITORY_BASE_URL);
        }

        mySets = Optional.ofNullable(aJsonObject.getJsonArray(SETS)).map(JsonArray::getList);

        if (scheduleCronExpression != null) {
            try {
                myScheduleCronExpression = new CronExpression(scheduleCronExpression);
            } catch (final ParseException details) {
                throw new InvalidJobJsonException(details, MessageCodes.PRL_004, SCHEDULE_CRON_EXPRESSION,
                        details.getMessage());
            }
        } else {
            throw new InvalidJobJsonException(MessageCodes.PRL_002, SCHEDULE_CRON_EXPRESSION);
        }

        myLastSuccessfulRun = Optional.ofNullable(aJsonObject.getString(LAST_SUCCESSFUL_RUN)).map(datetime -> {
            try {
                return OffsetDateTime.parse(datetime);
            } catch (final DateTimeParseException details) {
                throw new InvalidJobJsonException(details, MessageCodes.PRL_004, LAST_SUCCESSFUL_RUN,
                        details.getMessage());
            }
        });
    }

    /**
     * @return The JSON representation of the job
     */
    public JsonObject toJson() {
        final JsonObject json = new JsonObject(toSqlTemplateParametersMap());

        // Convert all the non-JSON types to JSON types
        getSets().ifPresent(sets -> json.put(SETS, new JsonArray(sets)));
        getLastSuccessfulRun().ifPresent(datetime -> json.put(LAST_SUCCESSFUL_RUN, datetime.toString()));

        return json;
    }

    /**
     * @return The job as a map that can be used with {@link SqlTemplate} queries
     */
    public Map<String, Object> toSqlTemplateParametersMap() {
        final Map<String, Object> map = new HashMap<>();

        map.put(INSTITUTION_ID, getInstitutionID());
        map.put(REPOSITORY_BASE_URL, getRepositoryBaseURL().toString());
        map.put(METADATA_PREFIX, getMetadataPrefix());
        // SqlTemplate parameter mapping requires that an array is represented as a Java array (not a List or JsonArray)
        map.put(SETS, getSets().map(sets -> sets.toArray(new String[0])).orElse(null));
        map.put(SCHEDULE_CRON_EXPRESSION, getScheduleCronExpression().getCronExpression());
        // Likewise, timestamps must be represented as OffsetDateTime (not a String)
        map.put(LAST_SUCCESSFUL_RUN, getLastSuccessfulRun().orElse(null));

        getID().ifPresent(id -> map.put(ID, id));

        return map;
    }

    /**
     * @return The optional ID
     */
    public Optional<Integer> getID() {
        return myID;
    }

    /**
     * @return The institution ID
     */
    public int getInstitutionID() {
        return myInstitutionID;
    }

    /**
     * @return The repository base URL
     */
    public URL getRepositoryBaseURL() {
        return myRepositoryBaseURL;
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
    public Optional<OffsetDateTime> getLastSuccessfulRun() {
        return myLastSuccessfulRun;
    }

    /**
     * @param aJob A job
     * @param aJobID The ID to associate with the job
     * @return A new Job with the optional {@link #ID} property
     */
    public static Job withID(final Job aJob, final Integer aJobID) {
        return new Job(aJob.toJson().put(ID, aJobID));
    }

    @Override
    public boolean equals(final Object anOther) {
        if (anOther instanceof Job) {
            final Job other = (Job) anOther;
            if (getID().equals(other.getID()) && getInstitutionID() == other.getInstitutionID() &&
                    getRepositoryBaseURL().equals(other.getRepositoryBaseURL()) &&
                    getMetadataPrefix().equals(other.getMetadataPrefix()) && getSets().equals(other.getSets()) &&
                    getScheduleCronExpression().getCronExpression()
                            .equals(other.getScheduleCronExpression().getCronExpression()) &&
                    getLastSuccessfulRun().equals(other.getLastSuccessfulRun())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + myID.map(id -> id.hashCode()).orElse(0);
        result = prime * result + myInstitutionID;
        result = prime * result + myRepositoryBaseURL.hashCode();
        result = prime * result + mySets.map(sets -> sets.hashCode()).orElse(0);
        result = prime * result + myScheduleCronExpression.getCronExpression().hashCode();
        result = prime * result + myLastSuccessfulRun.map(timestamp -> timestamp.hashCode()).orElse(0);

        return result;
    }

    @Override
    public String toString() {
        return toJson().encode();
    }
}
