
package edu.ucla.library.prl.harvester;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Represents the result of an OAI-PMH harvest job.
 */
@DataObject
public class JobResult {

    /**
     * The JSON key for the job ID.
     */
    static final String JOB_ID = "jobID";

    /**
     * The JSON key for the start time.
     */
    static final String START_TIME = "startTime";

    /**
     * The JSON key for the record count.
     */
    static final String RECORD_COUNT = "recordCount";

    /**
     * The JSON key for the deleted record count.
     */
    static final String DELETED_RECORD_COUNT = "deletedRecordCount";

    /**
     * The ID of the associated job.
     */
    private final int myJobID;

    /**
     * The time when the job was started.
     */
    private final OffsetDateTime myStartTime;

    /**
     * The number of records harvested.
     */
    private final int myRecordCount;

    /**
     * The number of records deleted.
     */
    private final int myDeletedRecordCount;

    /**
     * Instantiates a job result.
     *
     * @param aJobID The ID of the associated job
     * @param aStartTime The time when the job was started
     * @param aRecordCount The number of records harvested
     * @param aDeletedRecordCount The number of records deleted
     */
    public JobResult(final int aJobID, final OffsetDateTime aStartTime, final int aRecordCount,
            final int aDeletedRecordCount) {
        myJobID = aJobID;
        myStartTime = Objects.requireNonNull(aStartTime);
        myRecordCount = aRecordCount;
        myDeletedRecordCount = aDeletedRecordCount;
    }

    /**
     * Instantiates a job result from its JSON representation.
     *
     * @param aJsonObject A job result represented as JSON
     * @throws InvalidJobResultJsonException If the JSON representation is invalid
     */
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition", "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity" })
    public JobResult(final JsonObject aJsonObject) {
        Objects.requireNonNull(aJsonObject);

        final Integer jobID = aJsonObject.getInteger(JOB_ID);
        final String startTime = aJsonObject.getString(START_TIME);
        final Integer recordCount = aJsonObject.getInteger(RECORD_COUNT);
        final Integer deletedRecordCount = aJsonObject.getInteger(DELETED_RECORD_COUNT);

        if (jobID != null) {
            if (jobID >= 1) {
                myJobID = jobID.intValue();
            } else {
                throw new InvalidJobResultJsonException(MessageCodes.PRL_004, JOB_ID, jobID);
            }
        } else {
            throw new InvalidJobResultJsonException(MessageCodes.PRL_002, JOB_ID);
        }

        if (startTime != null) {
            try {
                myStartTime = OffsetDateTime.parse(startTime);
            } catch (final DateTimeParseException details) {
                throw new InvalidJobResultJsonException(details, MessageCodes.PRL_004, START_TIME,
                        details.getMessage());
            }
        } else {
            throw new InvalidJobResultJsonException(MessageCodes.PRL_002, START_TIME);
        }

        if (recordCount != null) {
            if (recordCount >= 0) {
                myRecordCount = recordCount.intValue();
            } else {
                throw new InvalidJobResultJsonException(MessageCodes.PRL_004, RECORD_COUNT, recordCount);
            }
        } else {
            throw new InvalidJobResultJsonException(MessageCodes.PRL_002, RECORD_COUNT);
        }

        if (deletedRecordCount != null) {
            if (deletedRecordCount >= 0) {
                myDeletedRecordCount = deletedRecordCount.intValue();
            } else {
                throw new InvalidJobResultJsonException(MessageCodes.PRL_004, DELETED_RECORD_COUNT, deletedRecordCount);
            }
        } else {
            throw new InvalidJobResultJsonException(MessageCodes.PRL_002, DELETED_RECORD_COUNT);
        }
    }

    /**
     * @return The JSON representation of the job result
     */
    public JsonObject toJson() {
        return new JsonObject() //
                .put(JOB_ID, getJobID()) //
                .put(START_TIME, getStartTime().toString()) //
                .put(RECORD_COUNT, getRecordCount()) //
                .put(DELETED_RECORD_COUNT, getDeletedRecordCount());
    }

    /**
     * @return The job ID
     */
    public int getJobID() {
        return myJobID;
    }

    /**
     * @return The start time
     */
    public OffsetDateTime getStartTime() {
        return myStartTime;
    }

    /**
     * @return The record count
     */
    public int getRecordCount() {
        return myRecordCount;
    }

    /**
     * @return The deleted record count
     */
    public int getDeletedRecordCount() {
        return myDeletedRecordCount;
    }

    @Override
    public boolean equals(final Object anOther) {
        if (anOther instanceof JobResult) {
            final JobResult other = (JobResult) anOther;

            if (getJobID() == other.getJobID() && getStartTime().equals(other.getStartTime()) &&
                    getRecordCount() == other.getRecordCount() &&
                    getDeletedRecordCount() == other.getDeletedRecordCount()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + myJobID;
        result = prime * result + myStartTime.hashCode();
        result = prime * result + myRecordCount;
        result = prime * result + myDeletedRecordCount;

        return result;
    }

    @Override
    public String toString() {
        return toJson().encode();
    }
}
