
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * Tests {@link JobResult}.
 */
@Execution(ExecutionMode.CONCURRENT)
public class JobResultTest {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JobResultTest.class, MessageCodes.BUNDLE);

    /**
     * Tests that a {@link JobResultTest} can be instantiated from a {@link JsonObject} and serialized back to one.
     */
    @Test
    void testJobResultSerDe() {
        final int exampleJobID = 1;
        final OffsetDateTime exampleStartTime = OffsetDateTime.parse("2000-01-01T00:00Z");
        final int exampleRecordCount = 10;

        final JobResult jobResult = new JobResult(exampleJobID, exampleStartTime, exampleRecordCount);
        final JsonObject json = new JsonObject() //
                .put(JobResult.JOB_ID, exampleJobID) //
                .put(JobResult.START_TIME, exampleStartTime.toString()) //
                .put(JobResult.RECORD_COUNT, exampleRecordCount);
        final JobResult jobResultFromJson = new JobResult(json);

        // If the JSON representations are equal, then serialization works
        assertEquals(json, jobResult.toJson());
        assertEquals(jobResult.toJson(), jobResultFromJson.toJson());

        // If the objects are equal, then deserialization works
        assertEquals(jobResult.getStartTime(), jobResultFromJson.getStartTime());
        assertEquals(jobResult.getRecordCount(), jobResultFromJson.getRecordCount());
    }

    /**
     * Tests that a {@link JobResult} cannot be instantiated from an invalid JSON representation.
     *
     * @param aJobID The ID of the associated job
     * @param aStartTime The time when the job was started
     * @param aRecordCount The number of records harvested for the job
     * @param anErrorClass The class of error that we expect instantiation with the above arguments to throw
     */
    @ParameterizedTest
    @MethodSource
    void testJobResultInvalidJsonRepresentation(final Integer aJobID, final String aStartTime,
            final Integer aRecordCount, final Class<Exception> anErrorClass) {
        final JsonObject json = new JsonObject() //
                .put(JobResult.JOB_ID, aJobID) //
                .put(JobResult.START_TIME, aStartTime) //
                .put(JobResult.RECORD_COUNT, aRecordCount);
        final Exception error = assertThrows(InvalidJobResultJsonException.class, () -> new JobResult(json));

        if (error.getCause() != null) {
            assertEquals(anErrorClass, error.getCause().getClass());
        }

        LOGGER.debug(LOGGER.getMessage(MessageCodes.PRL_000, error));
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws DateTimeParseException
     */
    static Stream<Arguments> testJobResultInvalidJsonRepresentation() throws DateTimeParseException {
        final String validTimestamp = OffsetDateTime.parse("2010-01-01T00:00Z").toString();
        final String invalidTimestamp = LocalDate.of(2020, 1, 1).toString(); // Missing time component

        return Stream.of( //
                Arguments.of(0, validTimestamp, 5, null), //
                Arguments.of(1, null, 10, null), //
                Arguments.of(2, invalidTimestamp, 50, DateTimeParseException.class), //
                Arguments.of(3, validTimestamp, null, null), //
                Arguments.of(4, validTimestamp, -1, null));
    }

    /**
     * Tests that the more strongly-typed constructor can't be called with certain arguments as null.
     */
    @Test
    void testJobResultNullArguments() {
        assertThrows(NullPointerException.class, () -> new JobResult(1, null, 10));
    }

    /**
     * Tests that passing a null {@link JsonObject} throws a {@link NullPointerException}.
     */
    @Test
    void testJobResultNullJsonObject() {
        assertThrows(NullPointerException.class, () -> new JobResult(null));
    }
}
