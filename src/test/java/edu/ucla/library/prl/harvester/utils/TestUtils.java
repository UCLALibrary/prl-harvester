
package edu.ucla.library.prl.harvester.utils;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.Param;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxTestContext.ExecutionBlock;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.templates.SqlTemplate;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

import org.jeasy.random.randomizers.EmailRandomizer;
import org.jeasy.random.randomizers.Ipv4AddressRandomizer;
import org.jeasy.random.randomizers.RegularExpressionRandomizer;
import org.jeasy.random.randomizers.SentenceRandomizer;
import org.jeasy.random.randomizers.time.OffsetDateTimeRandomizer;

import org.quartz.CronExpression;

/**
 * Utilities related to working with test objects.
 */
public final class TestUtils {

    public static final UriTemplate INSTITUTION = UriTemplate.of("/institutions/{id}");

    public static final UriTemplate INSTITUTIONS = UriTemplate.of("/institutions");

    public static final UriTemplate JOB = UriTemplate.of("/jobs/{id}");

    public static final UriTemplate JOBS = UriTemplate.of("/jobs");

    private static final Random RANDOMIZER = new Random();

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private static final String URL_PREFIX = "http://";

    private static final String CONTACT_POSTFIX = "/contact";

    private static final String QUESTION = "?";

    private static final EmailRandomizer RAND_EMAIL = new EmailRandomizer();

    private static final Ipv4AddressRandomizer RAND_URL = new Ipv4AddressRandomizer();

    private static final RegularExpressionRandomizer RAND_PHONE =
            new RegularExpressionRandomizer("^\\+1 310 [2-9]\\d{2} \\d{4}$");

    private static final SentenceRandomizer RAND_STRING = new SentenceRandomizer();

    private static final OffsetDateTimeRandomizer RAND_DATE = new OffsetDateTimeRandomizer();

    private static final String SOLR_SELECT_ALL = "*:*";

    private static final String SOLR_SELECT_BY_ID = "id:\"{}\"";

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class, MessageCodes.BUNDLE);

    private TestUtils() {
    }

    /**
     * Gets a random {@link Institution} for testing.
     *
     * @return A random Institution object
     */
    public static Institution getRandomInstitution()
            throws AddressException, MalformedURLException, NumberParseException {

        final String randName = RAND_STRING.getRandomValue();
        final String randDescription = RAND_STRING.getRandomValue();
        final String randLocation = RAND_STRING.getRandomValue();
        final Optional<InternetAddress> randEmail = Optional.of(new InternetAddress(RAND_EMAIL.getRandomValue()));
        final Optional<PhoneNumber> randPhone = Optional.of(PHONE_NUMBER_UTIL.parse(RAND_PHONE.getRandomValue(), null));
        final Optional<URL> randWebContact =
                Optional.of(new URL(URL_PREFIX.concat(RAND_URL.getRandomValue()).concat(CONTACT_POSTFIX)));
        final URL randWebsite = new URL(URL_PREFIX.concat(RAND_URL.getRandomValue()));

        return new Institution(randName, randDescription, randLocation, randEmail, randPhone, randWebContact,
                randWebsite);
    }

    /**
     * Gets a random {@link Job} for testing.
     *
     * @param anInstitutionID The ID of the institution to associate the job with
     * @return A random Job object
     */
    public static Job getRandomJob(final Integer anInstitutionID) throws MalformedURLException, ParseException {

        final int randListSize = RANDOMIZER.nextInt(5) + 1;
        final URL randURL = new URL(URL_PREFIX.concat(RAND_URL.getRandomValue()));
        final List<String> randSets = new ArrayList<>(randListSize);
        final OffsetDateTime randDate = RAND_DATE.getRandomValue();
        final CronExpression randCron = new CronExpression(buildCron(randDate));

        for (int index = 0; index < randListSize; index++) {
            randSets.add(RAND_STRING.getRandomValue().replaceAll("\\s", ""));
        }

        return new Job(anInstitutionID, randURL, randSets, randCron, null);
    }

    private static String buildCron(final OffsetDateTime aSourceDate) {
        final String blank = " ";
        final StringBuffer cronExpression = new StringBuffer();

        cronExpression.append(aSourceDate.getSecond()).append(blank);
        cronExpression.append(aSourceDate.getMinute()).append(blank);
        cronExpression.append(aSourceDate.getHour()).append(blank);
        cronExpression.append(aSourceDate.getDayOfMonth()).append(blank);
        cronExpression.append(aSourceDate.getMonthValue()).append(blank);
        cronExpression.append(QUESTION);
        return cronExpression.toString();
    }

    /**
     * Clears out the database.
     *
     * @param aConnectionPool A database connection pool
     * @return A Future that succeeds if the database was wiped successfully, and fails otherwise
     */
    public static Future<SqlResult<Void>> wipeDatabase(final Pool aConnectionPool) {
        return aConnectionPool.withConnection(connection -> {
            return SqlTemplate.forUpdate(connection, "TRUNCATE public.harvestjobs, public.institutions")
                    .execute(Map.of());
        });
    }

    /**
     * Clears out the Solr index.
     *
     * @param aSolrClient A Solr client
     * @return A Future that succeeds if the Solr index was wiped successfully, and fails otherwise
     */
    public static Future<UpdateResponse> wipeSolr(final JavaAsyncSolrClient aSolrClient) {
        final CompletionStage<UpdateResponse> wipeSolr =
                aSolrClient.deleteByQuery(SOLR_SELECT_ALL).thenCompose(result -> aSolrClient.commit());

        return Future.fromCompletionStage(wipeSolr);
    }

    /**
     * Clears out the Solr index of item records associated with a single institution.
     *
     * @param aSolrClient A Solr client
     * @param anInstitutionName The name of the institution whose item records should be removed
     * @return A Future that succeeds if the Solr index was modified successfully, and fails otherwise
     */
    public static Future<UpdateResponse> wipeSolrRecords(final JavaAsyncSolrClient aSolrClient,
            final String anInstitutionName) {
        final CompletionStage<UpdateResponse> wipeSolrRecords =
                aSolrClient.deleteByQuery(StringUtils.format("institutionName:\"{}\"", anInstitutionName))
                        .thenCompose(result -> aSolrClient.commit());

        return Future.fromCompletionStage(wipeSolrRecords);
    }

    /**
     * @param aSolrClient A Solr client
     * @return A Future that resolves to the list of all documents
     */
    public static Future<SolrDocumentList> getAllDocuments(final JavaAsyncSolrClient aSolrClient) {
        final CompletionStage<SolrDocumentList> results =
                aSolrClient.query(getSolrParamsFromQuery(SOLR_SELECT_ALL)).thenApply(QueryResponse::getResults);

        return Future.fromCompletionStage(results);
    }

    /**
     * @param aSolrClient A Solr client
     * @param anInstitutionName An institution name
     * @return A Future that resolves to the list of documents that should not exceed a size of 1
     */
    public static Future<SolrDocumentList> getInstitutionDoc(final JavaAsyncSolrClient aSolrClient,
            final String anInstitutionName) {
        final String query = StringUtils.format(SOLR_SELECT_BY_ID, anInstitutionName);
        final CompletionStage<SolrDocumentList> results =
                aSolrClient.query(getSolrParamsFromQuery(query)).thenApply(QueryResponse::getResults);

        return Future.fromCompletionStage(results);
    }

    /**
     * @param aQuery A Solr query string
     * @return The params
     */
    private static SolrParams getSolrParamsFromQuery(final String aQuery) {
        return new NamedList<>(Map.of("q", aQuery)).toSolrParams();
    }

    /**
     * @param anID An ID
     * @return An ID used to expand a {@link UriTemplate}
     */
    public static Variables getUriTemplateVars(final int anID) {
        return Variables.variables(new JsonObject().put(Param.id.name(), anID));
    }

    /**
     * Resets the application.
     * <p>
     * Use of the REST API is required because the application maintains the job schedule in-memory.
     *
     * @param aWebClient A web client with its default host and port set to point to the application
     * @return A Future that succeeds if the application was reset successfully, and fails otherwise
     */
    public static Future<Void> resetApplication(final WebClient aWebClient) {
        return aWebClient.get(JOBS).send().compose(response -> {
            final Stream<Integer> jobIDs = response.bodyAsJsonArray().stream().map(job -> new Job((JsonObject) job)
                    .getID().orElseThrow(() -> new NoSuchElementException(LOGGER.getMessage(MessageCodes.PRL_023))));
            @SuppressWarnings("rawtypes")
            final Function<Integer, Future> deleteJob = id -> {
                final String uri = JOB.expandToString(getUriTemplateVars(id));

                return aWebClient.delete(uri).expect(ResponsePredicate.SC_NO_CONTENT).send();
            };

            return CompositeFuture.all(jobIDs.map(deleteJob).toList());
        }).compose(result -> {
            return aWebClient.get(INSTITUTIONS).send().compose(response -> {
                final Stream<Integer> instIDs = response.bodyAsJsonArray().stream()
                        .map(inst -> new Institution((JsonObject) inst).getID().orElseThrow(
                                () -> new NoSuchElementException(LOGGER.getMessage(MessageCodes.PRL_022))));
                @SuppressWarnings("rawtypes")
                final Function<Integer, Future> deleteInst = id -> {
                    final String uri = INSTITUTION.expandToString(getUriTemplateVars(id));

                    return aWebClient.delete(uri).expect(ResponsePredicate.SC_NO_CONTENT).send();
                };

                return CompositeFuture.all(instIDs.map(deleteInst).toList());
            });
        }).compose(result -> {
            return result.mapEmpty();
        });
    }

    /**
     * Runs some assertions on institution rows in a database.
     *
     * @param aContext A test context
     * @param aCheckpoint A checkpoint that will be flagged if the appropriate assertions pass
     * @param aDbConnectionPool A database connection pool
     * @param anInstitution An institution that may or not be represented in the database
     * @param anExpectedExistence Whether or not an institution is expected to be represented in the database
     */
    public static void assertExpectedDatabaseInstitutionRow(final VertxTestContext aContext,
            final Checkpoint aCheckpoint, final Pool aDbConnectionPool, final Institution anInstitution,
            final boolean anExpectedExistence) {
        aDbConnectionPool.withConnection(connection -> {
            final String query = """
                SELECT id, name, description, location, email, phone, webContact AS "webContact", website
                FROM public.institutions
                WHERE name = $1
                """;

            return connection.preparedQuery(query).execute(Tuple.of(anInstitution.getName()));
        }).onSuccess(result -> {
            final ExecutionBlock checksToRun;

            if (anExpectedExistence) {
                checksToRun = () -> {
                    final JsonObject expected;
                    final Institution institution;

                    assertEquals(1, result.rowCount());

                    institution = new Institution(result.iterator().next().toJson());
                    expected = anInstitution.toJson().put(Institution.ID, institution.getID()
                            .orElseThrow(() -> new NoSuchElementException(LOGGER.getMessage(MessageCodes.PRL_022))));

                    assertEquals(expected, institution.toJson());

                    aCheckpoint.flag();
                };
            } else {
                checksToRun = () -> {
                    assertEquals(0, result.rowCount());

                    aCheckpoint.flag();
                };
            }

            aContext.verify(checksToRun);
        }).onFailure(aContext::failNow);
    }

    /**
     * Runs some assertions on job rows in a database.
     *
     * @param aContext A test context
     * @param aCheckpoint A checkpoint that will be flagged if the appropriate assertions pass
     * @param aDbConnectionPool A database connection pool
     * @param aJob A job that may or not be represented in the database
     * @param anExpectedExistence Whether or not a job is expected to be represented in the database
     */
    public static void assertExpectedDatabaseJobRow(final VertxTestContext aContext, final Checkpoint aCheckpoint,
            final Pool aDbConnectionPool, final Job aJob, final boolean anExpectedExistence) {
        aDbConnectionPool.withConnection(connection -> {
            final String query = """
                SELECT
                    id, institutionID AS "institutionID", repositoryBaseURL AS "repositoryBaseURL",
                    metadataPrefix AS "metadataPrefix", sets, lastSuccessfulRun AS "lastSuccessfulRun",
                    scheduleCronExpression AS "scheduleCronExpression"
                FROM public.harvestjobs
                WHERE repositoryBaseURL = $1
                """;

            return connection.preparedQuery(query).execute(Tuple.of(aJob.getRepositoryBaseURL().toString()));
        }).onSuccess(result -> {
            final ExecutionBlock checksToRun;

            if (anExpectedExistence) {
                checksToRun = () -> {
                    final JsonObject expected;
                    final Job job;

                    assertEquals(1, result.rowCount());

                    job = new Job(result.iterator().next().toJson());
                    expected = aJob.toJson().put(Job.ID, job.getID()
                            .orElseThrow(() -> new NoSuchElementException(LOGGER.getMessage(MessageCodes.PRL_023))));

                    assertEquals(expected, job.toJson());

                    aCheckpoint.flag();
                };
            } else {
                checksToRun = () -> {
                    assertEquals(0, result.rowCount());

                    aCheckpoint.flag();
                };
            }

            aContext.verify(checksToRun);
        }).onFailure(aContext::failNow);
    }

    /**
     * Runs some assertions on institution docs in a Solr index.
     *
     * @param aContext A test context
     * @param aCheckpoint A checkpoint
     * @param aSolrClient A Solr client
     * @param anInstitutionList The optional exhaustive list of institutions expected to be represented in the index
     */
    public static void assertExpectedSolrState(final VertxTestContext aContext, final Checkpoint aCheckpoint,
            final JavaAsyncSolrClient aSolrClient, final Optional<Set<Institution>> anInstitutionList) {
        getAllDocuments(aSolrClient).onSuccess(result -> {
            final ExecutionBlock checksToRun;

            if (anInstitutionList.isPresent()) {
                checksToRun = () -> {
                    final Set<Institution> institutions;

                    assertTrue(anInstitutionList.isPresent());

                    institutions = anInstitutionList.get();

                    assertEquals(institutions.size(), result.getNumFound());

                    for (final Institution institution : institutions) {
                        final SolrInputDocument input = institution.toSolrDoc();
                        final Optional<SolrDocument> output = result.stream()
                                .filter(doc -> doc.get(Institution.ID).equals(input.getFieldValue(Institution.ID)))
                                .findAny();

                        assertTrue(output.isPresent());
                        assertTrue(documentsAreEffectivelyEqual(input, output.get()));
                    }

                    aCheckpoint.flag();
                };
            } else {
                checksToRun = () -> {
                    assertEquals(0, result.getNumFound());

                    aCheckpoint.flag();
                };
            }

            aContext.verify(checksToRun);
        }).onFailure(aContext::failNow);
    }

    /**
     * @param anInput A Solr input document
     * @param anOutput A Solr output document
     * @return Whether or not the documents are effectively equalent
     */
    private static boolean documentsAreEffectivelyEqual(final SolrInputDocument anInput, final SolrDocument anOutput) {
        final Collection<String> inputFieldNames = anInput.getFieldNames();
        final Collection<String> outputFieldNames = anOutput.getFieldNames();

        // Consider all fields except _version_, which isn't present for input documents
        outputFieldNames.remove("_version_");

        if (!inputFieldNames.containsAll(outputFieldNames) || !outputFieldNames.containsAll(inputFieldNames)) {
            return false;
        }

        for (final String field : inputFieldNames) {
            if (!anInput.getFieldValues(field).equals(anOutput.getFieldValues(field))) {
                return false;
            }
        }

        return true;
    }
}
