
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.quartz.CronExpression;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * Tests {@link Job}.
 */
@Execution(ExecutionMode.CONCURRENT)
public class JobTest {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JobTest.class, MessageCodes.BUNDLE);

    /**
     * Tests that a {@link Job} can be instantiated from a {@link JsonObject} and serialized back to one.
     *
     * @param anInstitutionID The identifier of the institution that this job should be associated with
     * @param aRepositoryBaseURL The base URL of the OAI-PMH repository
     * @param aSets The list of sets to harvest; if empty, assume all sets should be harvested
     * @param aScheduleCronExpression The schedule on which this job should be run
     * @param aLastSuccessfulRun The timestamp of the last successful run of this job; will be null at first
     */
    @ParameterizedTest
    @MethodSource
    void testJobSerDe(final int anInstitutionID, final URL aRepositoryBaseURL, final List<String> aSets,
            final CronExpression aScheduleCronExpression, final OffsetDateTime aLastSuccessfulRun) {
        final Job job =
                new Job(anInstitutionID, aRepositoryBaseURL, aSets, aScheduleCronExpression, aLastSuccessfulRun);
        final JsonObject json = new JsonObject() //
                .put(Job.INSTITUTION_ID, anInstitutionID) //
                .put(Job.REPOSITORY_BASE_URL, aRepositoryBaseURL.toString()) //
                .put(Job.SETS, Optional.ofNullable(aSets).orElse(null)) //
                .put(Job.SCHEDULE_CRON_EXPRESSION, aScheduleCronExpression.getCronExpression()) //
                .put(Job.LAST_SUCCESSFUL_RUN,
                        Optional.ofNullable(aLastSuccessfulRun).map(OffsetDateTime::toString).orElse(null));
        final Job jobFromJson = new Job(json);

        // If the JSON representations are equal, then serialization works
        assertEquals(json.copy().put(Job.METADATA_PREFIX, Constants.OAI_DC), job.toJson());
        assertEquals(job.toJson(), jobFromJson.toJson());

        // If the objects are equal, then deserialization works
        assertEquals(job.getInstitutionID(), jobFromJson.getInstitutionID());
        assertEquals(job.getRepositoryBaseURL(), jobFromJson.getRepositoryBaseURL());
        assertEquals(job.getSets(), jobFromJson.getSets());
        assertEquals(job.getMetadataPrefix(), jobFromJson.getMetadataPrefix());
        assertEquals(job.getScheduleCronExpression().toString(), jobFromJson.getScheduleCronExpression().toString());
        assertEquals(job.getLastSuccessfulRun(), jobFromJson.getLastSuccessfulRun());

        assertEquals(job, jobFromJson);
        assertEquals(job.hashCode(), jobFromJson.hashCode());
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws MalformedURLException
     * @throws ParseException
     */
    static Stream<Arguments> testJobSerDe() throws MalformedURLException, ParseException {
        final URL exampleURL = new URL("http://example.com/1/oai");
        final List<String> exampleSets = List.of("set1:subset1", "set1:subset2");
        final CronExpression exampleSchedule = new CronExpression("0 0 3 1 * ?");
        final OffsetDateTime exampleTimestamp = OffsetDateTime.parse("2000-01-01T00:00Z");

        return Stream.of( //
                Arguments.of(1, exampleURL, List.of(), exampleSchedule, exampleTimestamp), //
                Arguments.of(2, exampleURL, exampleSets, exampleSchedule, exampleTimestamp), //
                Arguments.of(3, exampleURL, List.of(), exampleSchedule, null), //
                Arguments.of(4, exampleURL, exampleSets, exampleSchedule, null));
    }

    /**
     * Tests that {@link Job#withID} adds an ID to the otherwise-unchanged {@link Job}.
     *
     * @param anInstitutionID The identifier of the institution that this job should be associated with
     * @param aRepositoryBaseURL The base URL of the OAI-PMH repository
     * @param aSets The list of sets to harvest; if empty, assume all sets should be harvested
     * @param aScheduleCronExpression The schedule on which this job should be run
     * @param aLastSuccessfulRun The timestamp of the last successful run of this job; will be null at first
     */
    @ParameterizedTest
    @MethodSource
    void testJobWithID(final int anInstitutionID, final URL aRepositoryBaseURL, final List<String> aSets,
            final CronExpression aScheduleCronExpression, final OffsetDateTime aLastSuccessfulRun) {
        final Job job =
                new Job(anInstitutionID, aRepositoryBaseURL, aSets, aScheduleCronExpression, aLastSuccessfulRun);
        final int jobID = 1;
        final Job jobWithID = Job.withID(job, jobID);
        final Job jobFromJsonWithID = new Job(job.toJson().put(Job.ID, jobID));

        assertNotEquals(job, jobWithID);
        assertNotEquals(job.toJson(), jobWithID.toJson());

        if (job.hashCode() == jobWithID.hashCode()) {
            LOGGER.warn(MessageCodes.PRL_044, Job.class.getName(), job, jobWithID);
        }

        // Adding id makes them equal
        assertEquals(jobWithID, jobFromJsonWithID);
        assertEquals(jobWithID.toJson(), jobFromJsonWithID.toJson());
        assertEquals(jobWithID.hashCode(), jobFromJsonWithID.hashCode());
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws MalformedURLException
     * @throws ParseException
     */
    static Stream<Arguments> testJobWithID() throws MalformedURLException, ParseException {
        return testJobSerDe();
    }

    /**
     * Tests that a {@link Job} cannot be instantiated from an invalid JSON representation.
     *
     * @param anInstitutionID The identifier of the institution that this job should be associated with
     * @param aRepositoryBaseURL The base URL of the OAI-PMH repository
     * @param aSets The list of sets to harvest; if empty, assume all sets should be harvested
     * @param aScheduleCronExpression The schedule on which this job should be run
     * @param aLastSuccessfulRun The timestamp of the last successful run of this job; will be null at first
     * @param anErrorClass The class of error that we expect instantiation with the above arguments to throw
     */
    @ParameterizedTest
    @MethodSource
    void testJobInvalidJsonRepresentation(final Integer anInstitutionID, final String aRepositoryBaseURL,
            final List<String> aSets, final String aScheduleCronExpression, final String aLastSuccessfulRun,
            final Class<Exception> anErrorClass) {
        final JsonObject json = new JsonObject() //
                .put(Job.INSTITUTION_ID, anInstitutionID) //
                .put(Job.REPOSITORY_BASE_URL, aRepositoryBaseURL) //
                .put(Job.SETS, aSets) //
                .put(Job.SCHEDULE_CRON_EXPRESSION, aScheduleCronExpression) //
                .put(Job.LAST_SUCCESSFUL_RUN, aLastSuccessfulRun);
        final Exception error = assertThrows(InvalidJobJsonException.class, () -> new Job(json));

        if (error.getCause() != null) {
            assertEquals(anErrorClass, error.getCause().getClass());
        }

        LOGGER.debug(MessageCodes.PRL_000, error);
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws MalformedURLException
     * @throws ParseException
     */
    static Stream<Arguments> testJobInvalidJsonRepresentation() throws MalformedURLException, ParseException {
        final String validURL = new URL("http://example.com/2/oai").toString();
        final String invalidURL = "example.com/oai"; // Missing protocol

        final List<String> validSets = List.of();

        final String validSchedule = new CronExpression("* * * * * ?").toString();
        final String invalidSchedule = "* * * * *"; // Minimum of six elements is required

        final String validTimestamp = OffsetDateTime.parse("2010-01-01T00:00Z").toString();
        final String invalidTimestamp = LocalDate.of(2020, 1, 1).toString(); // Missing time component

        return Stream.of( //
                Arguments.of(null, validURL, validSets, validSchedule, validTimestamp, null), //
                Arguments.of(2, null, validSets, validSchedule, validTimestamp, null), //
                Arguments.of(3, invalidURL, validSets, validSchedule, validTimestamp, MalformedURLException.class), //
                Arguments.of(4, validURL, validSets, null, validTimestamp, null), //
                Arguments.of(5, validURL, validSets, invalidSchedule, validTimestamp, ParseException.class), //
                Arguments.of(6, validURL, validSets, validSchedule, invalidTimestamp, DateTimeParseException.class));
    }

    /**
     * Tests that the more strongly-typed constructor can't be called with certain arguments as null.
     *
     * @param anInstitutionID The identifier of the institution that this job should be associated with
     * @param aRepositoryBaseURL The base URL of the OAI-PMH repository
     * @param aSets The list of sets to harvest; if empty, assume all sets should be harvested
     * @param aScheduleCronExpression The schedule on which this job should be run
     * @param aLastSuccessfulRun The timestamp of the last successful run of this job; will be null at first
     */
    @ParameterizedTest
    @MethodSource
    void testJobNullArguments(final int anInstitutionID, final URL aRepositoryBaseURL, final List<String> aSets,
            final CronExpression aScheduleCronExpression, final OffsetDateTime aLastSuccessfulRun) {
        assertThrows(NullPointerException.class, () -> {
            new Job(anInstitutionID, aRepositoryBaseURL, aSets, aScheduleCronExpression, aLastSuccessfulRun);
        });
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws MalformedURLException
     * @throws ParseException
     */
    static Stream<Arguments> testJobNullArguments() throws MalformedURLException, ParseException {
        final URL validURL = new URL("http://example.com/3/oai");
        final List<String> validSets = List.of();
        final CronExpression validSchedule = new CronExpression("0 0 * * * ?");
        final OffsetDateTime validTimestamp = OffsetDateTime.parse("2020-01-01T00:00Z");

        return Stream.of( //
                Arguments.of(1, null, validSets, validSchedule, validTimestamp), //
                Arguments.of(2, validURL, validSets, null, validTimestamp), //
                Arguments.of(3, null, validSets, null, validTimestamp));
    }

    /**
     * Tests that passing a null {@link JsonObject} throws a {@link NullPointerException}.
     */
    @Test
    void testJobNullJsonObject() {
        assertThrows(NullPointerException.class, () -> new Job(null));
    }
}
