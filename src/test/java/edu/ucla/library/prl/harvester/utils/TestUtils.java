
package edu.ucla.library.prl.harvester.utils;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

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

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.Param;

import io.ino.solrs.JavaAsyncSolrClient;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.templates.SqlTemplate;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;

/**
 * Utilities related to working with test objects.
 */
public final class TestUtils {

    /**
     * The ENV property for the application's host.
     */
    public static final String HTTP_HOST = "HTTP_HOST";

    /**
     * The ENV property for the test user's LDAP username.
     */
    public static final String LDAP_USERNAME = "LDAP_USERNAME";

    /**
     * The ENV property for the test user's LDAP password.
     */
    public static final String LDAP_PASSWORD = "LDAP_PASSWORD";

    /**
     * The ENV property property for the data provider's base URL.
     */
    public static final String TEST_PROVIDER_BASE_URL = "TEST_PROVIDER_BASE_URL";

    public static final UriTemplate INSTITUTION = UriTemplate.of("/institutions/{id}");

    public static final UriTemplate INSTITUTIONS = UriTemplate.of("/institutions");

    public static final UriTemplate JOB = UriTemplate.of("/jobs/{id}");

    public static final UriTemplate JOBS = UriTemplate.of("/jobs");

    public static final String SET1 = "set1";

    public static final String SET2 = "set2";

    public static final int SET1_RECORD_COUNT = 2;

    public static final int SET2_RECORD_COUNT = 3;

    private static final Random RANDOMIZER = new Random();

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private static final String URL_PREFIX = "http://";

    private static final String CONTACT_POSTFIX = "/contact";

    private static final EmailRandomizer RAND_EMAIL = new EmailRandomizer();

    private static final Ipv4AddressRandomizer RAND_URL = new Ipv4AddressRandomizer();

    private static final RegularExpressionRandomizer RAND_PHONE =
            new RegularExpressionRandomizer("^\\+1 310 [2-9]\\d{2} \\d{4}$");

    private static final SentenceRandomizer RAND_STRING = new SentenceRandomizer();

    private static final OffsetDateTimeRandomizer RAND_DATE = new OffsetDateTimeRandomizer();

    private static final String SOLR_SELECT_ALL = "*:*";

    private static final String SOLR_ID_INCLUDE = "id:{}";

    private static final String SOLR_ID_EXCLUDE = "id:(-{})";

    private static final String INSTITUTION_DOC_ID_PATTERN = StringUtils.format(Institution.SOLR_DOC_ID_TEMPLATE, "*");

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
        return String.format("%d %d %d %d %d ?", aSourceDate.getSecond(), aSourceDate.getMinute(),
                aSourceDate.getHour(), aSourceDate.getDayOfMonth(), aSourceDate.getMonthValue());
    }

    /**
     * Gets a job for testing against our test OAI-PMH repository.
     *
     * @param anInstitutionID The ID of the institution to associate the job with
     * @param aBaseURL An OAI-PMH base URL that resolves to a repository
     * @param aSets A list of OAI-PMH set specs that are defined on that repository
     * @return A job object with data that matches our test setup
     * @throws ParseException If there is something wrong with {@link #buildCron}
     */
    public static Job getJob(final Integer anInstitutionID, final URL aBaseURL, final List<String> aSets)
            throws ParseException {
        final CronExpression randCron = new CronExpression(buildCron(RAND_DATE.getRandomValue()));

        return new Job(anInstitutionID, aBaseURL, aSets, randCron, null);
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
        final CompletionStage<UpdateResponse> deletion =
                aSolrClient.deleteByQuery(SOLR_SELECT_ALL).thenCompose(result -> aSolrClient.commit());

        return Future.fromCompletionStage(deletion);
    }

    /**
     * Removes item records associated with a single institution from the Solr index.
     *
     * @param aSolrClient A Solr client
     * @param anInstitutionName The name of the institution whose item records should be removed
     * @return A Future that succeeds if the Solr index was modified successfully, and fails otherwise
     */
    public static Future<UpdateResponse> removeItemRecords(final JavaAsyncSolrClient aSolrClient,
            final String anInstitutionName) {
        final String query = StringUtils.format("institutionName:\"{}\"", anInstitutionName);
        final CompletionStage<UpdateResponse> deletion =
                aSolrClient.deleteByQuery(query).thenCompose(result -> aSolrClient.commit());

        return Future.fromCompletionStage(deletion);
    }

    /**
     * @param aSolrClient A Solr client
     * @return A Future that resolves to the list of all documents
     */
    public static Future<SolrDocumentList> getAllDocuments(final JavaAsyncSolrClient aSolrClient) {
        return getDocuments(aSolrClient, SOLR_SELECT_ALL);
    }

    /**
     * @param aSolrClient A Solr client
     * @return A Future that resolves to the list of institution documents
     */
    public static Future<SolrDocumentList> getInstitutionDocuments(final JavaAsyncSolrClient aSolrClient) {
        return getDocuments(aSolrClient, StringUtils.format(SOLR_ID_INCLUDE, INSTITUTION_DOC_ID_PATTERN));
    }

    /**
     * @param aSolrClient A Solr client
     * @return A Future that resolves to the list of item record documents
     */
    public static Future<SolrDocumentList> getItemRecordDocuments(final JavaAsyncSolrClient aSolrClient) {
        return getDocuments(aSolrClient, StringUtils.format(SOLR_ID_EXCLUDE, INSTITUTION_DOC_ID_PATTERN));
    }

    /**
     * @param aSolrClient A Solr client
     * @param aQuery A Solr query
     * @return A Future that resolves to the list of documents that match the query
     */
    public static Future<SolrDocumentList> getDocuments(final JavaAsyncSolrClient aSolrClient, final String aQuery) {
        final SolrParams params = new NamedList<>(Map.of("q", aQuery)).toSolrParams();
        final CompletionStage<SolrDocumentList> retrieval =
                aSolrClient.query(params).thenApply(QueryResponse::getResults);

        return Future.fromCompletionStage(retrieval);
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
            // First, delete all the jobs
            final Stream<Future<HttpResponse<Buffer>>> jobDeletions = response.bodyAsJsonArray().stream().map(job -> {
                final int id = unwrapJobID(new Job((JsonObject) job));
                final String uri = JOB.expandToString(getUriTemplateVars(id));

                return aWebClient.delete(uri).expect(ResponsePredicate.SC_NO_CONTENT).send();
            });

            return CompositeFuture.all(jobDeletions.collect(Collectors.toList()));
        }).compose(result -> {
            return aWebClient.get(INSTITUTIONS).send().compose(response -> {
                // Then, delete all the institutions
                final Stream<Future<HttpResponse<Buffer>>> instDeletions =
                        response.bodyAsJsonArray().stream().map(institution -> {
                            final int id = unwrapInstitutionID(new Institution((JsonObject) institution));
                            final String uri = INSTITUTION.expandToString(getUriTemplateVars(id));

                            return aWebClient.delete(uri).expect(ResponsePredicate.SC_NO_CONTENT).send();
                        });

                return CompositeFuture.all(instDeletions.collect(Collectors.toList()));
            });
        }).mapEmpty();
    }

    /**
     * Constructs some assertions for the institutions table in the database.
     *
     * @param aDbConnectionPool A database connection pool
     * @param anInstitutionList The optional exhaustive list of institutions that must be represented in the database
     * @return A Future that resolves to a Runnable consisting of the assertions
     */
    public static Future<Runnable> getDatabaseInstitutionAssertions(final Pool aDbConnectionPool,
            final Optional<Set<Institution>> anInstitutionList) {
        return aDbConnectionPool.withConnection(connection -> {
            final String query = """
                SELECT name, description, location, email, phone, webContact AS "webContact", website
                FROM public.institutions
                """;

            return connection.query(query).execute();
        }).map(result -> {
            final Runnable assertions;

            if (anInstitutionList.isPresent()) {
                assertions = () -> {
                    final Set<Institution> institutions = anInstitutionList.get();

                    assertEquals(institutions.size(), result.rowCount());

                    for (final Institution institution : institutions) {
                        assertTrue(matchingRowExists(result, institution.toJson()));
                    }
                };
            } else {
                assertions = () -> {
                    assertEquals(0, result.rowCount());
                };
            }

            return assertions;
        });
    }

    /**
     * Constructs some assertions for the jobs table in the database.
     *
     * @param aDbConnectionPool A database connection pool
     * @param aJobList The optional exhaustive list of jobs that must be represented in the database
     * @return A Future that resolves to a Runnable consisting of the assertions
     */
    public static Future<Runnable> getDatabaseJobAssertions(final Pool aDbConnectionPool,
            final Optional<Set<Job>> aJobList) {
        return aDbConnectionPool.withConnection(connection -> {
            final String query = """
                SELECT
                    institutionID AS "institutionID", repositoryBaseURL AS "repositoryBaseURL",
                    metadataPrefix AS "metadataPrefix", sets, lastSuccessfulRun AS "lastSuccessfulRun",
                    scheduleCronExpression AS "scheduleCronExpression"
                FROM public.harvestjobs
                """;

            return connection.query(query).execute();
        }).map(result -> {
            final Runnable assertions;

            if (aJobList.isPresent()) {
                assertions = () -> {
                    final Set<Job> jobs = aJobList.get();

                    assertEquals(jobs.size(), result.rowCount());

                    for (final Job job : jobs) {
                        assertTrue(matchingRowExists(result, job.toJson()));
                    }
                };
            } else {
                assertions = () -> {
                    assertEquals(0, result.rowCount());
                };
            }

            return assertions;
        });
    }

    /**
     * Constructs some assertions for the institution docs in the Solr index.
     *
     * @param aSolrClient A Solr client
     * @param anInstitutionList The optional exhaustive list of institutions that must be represented in the index
     * @return A Future that resolves to a Runnable consisting of the assertions
     */
    public static Future<Runnable> getSolrInstitutionAssertions(final JavaAsyncSolrClient aSolrClient,
            final Optional<Set<Institution>> anInstitutionList) {
        return getInstitutionDocuments(aSolrClient).map(result -> {
            final Runnable assertions;

            if (anInstitutionList.isPresent()) {
                assertions = () -> {
                    final Set<Institution> institutions = anInstitutionList.get();

                    assertEquals(institutions.size(), result.getNumFound());

                    for (final Institution institution : institutions) {
                        final SolrInputDocument input = institution.toSolrDoc();
                        final Optional<SolrDocument> output = result.stream()
                                .filter(doc -> doc.get(Institution.ID).equals(input.getFieldValue(Institution.ID)))
                                .findAny();

                        assertTrue(output.isPresent());
                        assertTrue(documentsAreEffectivelyEqual(input, output.get()));
                    }
                };
            } else {
                assertions = () -> {
                    assertEquals(0, result.getNumFound());
                };
            }

            return assertions;
        });
    }

    /**
     * Constructs some assertions for the item record docs in the Solr index.
     *
     * @param aSolrClient A Solr client
     * @param anExpectedRecordCount The number of item record docs that should exist
     * @return A Future that resolves to a Runnable consisting of the assertions
     */
    public static Future<Runnable> getSolrItemRecordAssertions(final JavaAsyncSolrClient aSolrClient,
            final int anExpectedRecordCount) {
        return getItemRecordDocuments(aSolrClient).map(result -> {
            final Runnable assertions = () -> {
                assertEquals(anExpectedRecordCount, result.getNumFound());
            };

            return assertions;
        });
    }

    /**
     * @param aRows Database query results
     * @param anExpectedRowAsJson The JSON representation that exactly one of the rows should have
     * @return Whether there exists a row that matches the expected JSON representation
     */
    private static boolean matchingRowExists(final RowSet<Row> aRows, final JsonObject anExpectedRowAsJson) {
        final RowIterator<Row> iterator = aRows.iterator();

        return Stream.generate(iterator::next).limit(1000000).filter(row -> {
            return row.toJson().equals(anExpectedRowAsJson);
        }).findAny().isPresent();
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

    /**
     * @param anInstitution An institution
     * @return The institution's ID
     * @throws NoSuchElementException If the institution doesn't have an ID
     */
    public static int unwrapInstitutionID(final Institution anInstitution) {
        return anInstitution.getID().orElseThrow(() -> {
            return new NoSuchElementException(LOGGER.getMessage(MessageCodes.PRL_022));
        });
    }

    /**
     * @param aJob A job
     * @return The job's ID
     * @throws NoSuchElementException If the job doesn't have an ID
     */
    public static int unwrapJobID(final Job aJob) {
        return aJob.getID().orElseThrow(() -> {
            return new NoSuchElementException(LOGGER.getMessage(MessageCodes.PRL_023));
        });
    }

    /**
     * Gets a Cron expression that will match some time in the future.
     *
     * @param aSecondsLater The number of seconds in the future to create an hourly Cron expression for
     * @return The Cron expression
     * @throws ParseException
     */
    public static CronExpression getFutureCronExpression(final long aSecondsLater) throws ParseException {
        final OffsetDateTime futureTime = OffsetDateTime.now().plusSeconds(aSecondsLater);
        final String cron = String.format("%d %d * * * ?", futureTime.getSecond(), futureTime.getMinute());

        LOGGER.debug(MessageCodes.PRL_033, aSecondsLater, cron);

        return new CronExpression(cron);
    }
}
