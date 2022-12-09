
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
     * The JSON key for the start time.
     */
    static final String START_TIME = "startTime";

    /**
     * The JSON key for the record count.
     */
    static final String RECORD_COUNT = "recordCount";

    /**
     * The time when the job was started.
     */
    private final OffsetDateTime myStartTime;

    /**
     * The number of records harvested.
     */
    private final int myRecordCount;

    /**
     * Instantiates a job result.
     *
     * @param aStartTime The time when the job was started
     * @param aRecordCount The number of records harvested
     */
    public JobResult(final OffsetDateTime aStartTime, final int aRecordCount) {
        myStartTime = Objects.requireNonNull(aStartTime);
        myRecordCount = aRecordCount;
    }

    /**
     * Instantiates a job result from its JSON representation.
     * <p>
     * <b>This constructor is meant to be used only by generated service proxy code!</b>
     * {@link #JobResult(OffsetDateTime, int)} should be used everywhere else.
     *
     * @param aJsonObject A job result represented as JSON
     * @throws InvalidJobResultJsonException If the JSON representation is invalid
     */
    public JobResult(final JsonObject aJsonObject) {
        Objects.requireNonNull(aJsonObject);

        final String startTime = aJsonObject.getString(START_TIME);
        final Integer recordCount = aJsonObject.getInteger(RECORD_COUNT);

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
    }

    /**
     * @return The JSON representation of the job result
     */
    public JsonObject toJson() {
        return new JsonObject().put(START_TIME, getStartTime().toString()).put(RECORD_COUNT, getRecordCount());
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
}
