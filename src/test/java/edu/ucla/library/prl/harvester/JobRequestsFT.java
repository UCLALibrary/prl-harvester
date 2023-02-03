
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.text.ParseException;

import javax.mail.internet.AddressException;

import org.apache.http.HttpStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.i18n.phonenumbers.NumberParseException;

import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Pool;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;

/**
 * Tests the application's behavior in response to requests involving {@link Job}s.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class JobRequestsFT {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobRequestsFT.class, MessageCodes.BUNDLE);

    private static final UriTemplate INSTITUTIONS = UriTemplate.of("/institutions");

    private static final UriTemplate JOB = UriTemplate.of("/jobs/{id}");

    private static final UriTemplate JOBS = UriTemplate.of("/jobs");

    private Pool myDbConnectionPool;

    private WebClient myWebClient;

    private int myInstitutionID;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final String host = config.getString(Config.HTTP_HOST);
            final int port = config.getInteger(Config.HTTP_PORT);

            myDbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            myWebClient = WebClient.create(aVertx, new WebClientOptions().setDefaultHost(host).setDefaultPort(port));

            return Future.succeededFuture();
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @throws AddressException
     * @throws MalformedURLException
     * @throws NumberParseException
     */
    @BeforeEach
    public void beforeEach(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        myWebClient.post(INSTITUTIONS).sendJson(TestUtils.getRandomInstitution().toJson()).onSuccess(response -> {
            myInstitutionID = new Institution(response.bodyAsJsonObject()).getID().get();

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterEach
    public void afterEach(final Vertx aVertx, final VertxTestContext aContext) {
        TestUtils.wipeDatabase(myDbConnectionPool).onSuccess(nil -> aContext.completeNow())
                .onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public final void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.close();
        myDbConnectionPool.close().onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#listJobs} initially retrieves an empty list.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testListBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.get(JOBS).send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_OK, response.statusCode());
                assertTrue(response.bodyAsJsonArray().isEmpty());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#listJobs} after {@link Op#addJob} retrieves a non-empty list.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @throws MalformedURLException
     * @throws ParseException
     */
    @Test
    void testListAfterAdd(final Vertx aVertx, final VertxTestContext aContext)
            throws MalformedURLException, ParseException {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Job job = TestUtils.getRandomJob(myInstitutionID);
        final Future<HttpResponse<Buffer>> addJob;

        // First request
        addJob = myWebClient.post(JOBS).sendJson(job.toJson());

        addJob.compose(addJobResponse -> {
            final Job responseJob = new Job(addJobResponse.bodyAsJsonObject());
            final Future<HttpResponse<Buffer>> listJobs;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobResponse.statusCode());
                assertTrue(responseJob.getID().isPresent());

                responseVerified.flag();
            });

            // Second request
            listJobs = myWebClient.get(JOBS).expect(ResponsePredicate.JSON).send();

            return listJobs.compose(listJobsResponse -> {
                final Job responseJob2 = new Job(listJobsResponse.bodyAsJsonArray().getJsonObject(0));

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, listJobsResponse.statusCode());
                    assertEquals(responseJob.toJson(), responseJob2.toJson());

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getJob} after {@link Op#addJob} retrieves the same data that was sent.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @throws MalformedURLException
     * @throws ParseException
     */
    @Test
    void testGetAfterAdd(final Vertx aVertx, final VertxTestContext aContext)
            throws MalformedURLException, ParseException {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Job job = TestUtils.getRandomJob(myInstitutionID);
        final Future<HttpResponse<Buffer>> addJob;

        // First request
        addJob = myWebClient.post(JOBS).sendJson(job.toJson());

        addJob.compose(addJobResponse -> {
            final Job responseJob = new Job(addJobResponse.bodyAsJsonObject());
            final Variables jobID;
            final Future<HttpResponse<Buffer>> getJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobResponse.statusCode());
                assertTrue(responseJob.getID().isPresent());

                responseVerified.flag();
            });

            // Second request
            jobID = TestUtils.getUriTemplateVars(responseJob.getID().get());
            getJob = myWebClient.get(JOB.expandToString(jobID)).expect(ResponsePredicate.JSON).send();

            return getJob.compose(getJobResponse -> {
                final Job responseJob2 = new Job(getJobResponse.bodyAsJsonObject());

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, getJobResponse.statusCode());
                    assertEquals(responseJob.toJson(), responseJob2.toJson());

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getJob} before {@link Op#addJob} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.get(JOB.expandToString(TestUtils.getUriTemplateVars(1))).send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#addJob} with invalid JSON results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @throws MalformedURLException
     * @throws ParseException
     */
    @Test
    void testAddInvalidJob(final Vertx aVertx, final VertxTestContext aContext)
            throws MalformedURLException, ParseException {
        final Job validJob = TestUtils.getRandomJob(myInstitutionID);
        final JsonObject invalidJobJson = validJob.toJson().put(Job.INSTITUTION_ID, null);

        myWebClient.post(JOBS).sendJson(invalidJobJson).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }
}
