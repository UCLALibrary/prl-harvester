
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
     */
    @BeforeEach
    public void beforeEach(final Vertx aVertx, final VertxTestContext aContext) {
        final Institution institution;

        try {
            institution = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        myWebClient.post(INSTITUTIONS).sendJson(institution.toJson()).onSuccess(response -> {
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
     */
    @Test
    void testListAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Job job;
        final Future<HttpResponse<Buffer>> addJob;

        try {
            job = TestUtils.getRandomJob(myInstitutionID);
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

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
     */
    @Test
    void testGetAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Job job;
        final Future<HttpResponse<Buffer>> addJob;

        try {
            job = TestUtils.getRandomJob(myInstitutionID);
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

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
     * Tests that {@link Op#getJob} after {@link Op#updateJob} retrieves different data than was sent in the initial
     * {@link Op#addJob}.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterUpdateAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(3);
        final Job job;
        final Job updatedJob;
        final Future<HttpResponse<Buffer>> addJob;

        try {
            job = TestUtils.getRandomJob(myInstitutionID);
            updatedJob = TestUtils.getRandomJob(myInstitutionID);
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJob = myWebClient.post(JOBS).expect(ResponsePredicate.JSON).sendJson(job.toJson());

        addJob.compose(addJobResponse -> {
            final Job responseJob = new Job(addJobResponse.bodyAsJsonObject());
            final Variables jobID;
            final Future<HttpResponse<Buffer>> updateJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobResponse.statusCode());
                assertTrue(responseJob.getID().isPresent());

                responseVerified.flag();
            });

            // Second request
            jobID = TestUtils.getUriTemplateVars(responseJob.getID().get());
            updateJob = myWebClient.put(JOB.expandToString(jobID)).expect(ResponsePredicate.JSON)
                    .sendJson(updatedJob.toJson());

            return updateJob.compose(updateJobResponse -> {
                final Job responseJob2 = new Job(updateJobResponse.bodyAsJsonObject());
                final Future<HttpResponse<Buffer>> getJob;

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, updateJobResponse.statusCode());
                    assertNotEquals(responseJob.toJson(), responseJob2.toJson());

                    responseVerified.flag();
                });

                // Third request
                getJob = myWebClient.get(JOB.expandToString(jobID)).expect(ResponsePredicate.JSON).send();

                return getJob.compose(getJobResponse -> {
                    final Job responseJob3 = new Job(getJobResponse.bodyAsJsonObject());

                    aContext.verify(() -> {
                        assertEquals(HttpStatus.SC_OK, getJobResponse.statusCode());
                        assertEquals(responseJob2.toJson(), responseJob3.toJson());

                        responseVerified.flag();
                    });

                    return Future.succeededFuture();
                });
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getJob} after {@link Op#removeJob} after {@link Op#addJob} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterRemoveAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(3);
        final Job job;
        final Future<HttpResponse<Buffer>> addJob;

        try {
            job = TestUtils.getRandomJob(myInstitutionID);
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJob = myWebClient.post(JOBS).expect(ResponsePredicate.JSON).sendJson(job.toJson());

        addJob.compose(addJobResponse -> {
            final Job responseJob = new Job(addJobResponse.bodyAsJsonObject());
            final Variables jobID;
            final Future<HttpResponse<Buffer>> removeJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobResponse.statusCode());
                assertTrue(responseJob.getID().isPresent());

                responseVerified.flag();
            });

            // Second request
            jobID = TestUtils.getUriTemplateVars(responseJob.getID().get());
            removeJob = myWebClient.delete(JOB.expandToString(jobID)).send();

            return removeJob.compose(removeJobResponse -> {
                final Future<HttpResponse<Buffer>> getJob;

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_NO_CONTENT, removeJobResponse.statusCode());

                    responseVerified.flag();
                });

                // Third request
                getJob = myWebClient.get(JOB.expandToString(jobID)).send();

                return getJob;
            }).compose(getJobResponse -> {
                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_NOT_FOUND, getJobResponse.statusCode());

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
     * Tests that {@link Op#updateJob} before {@link Op#addJob} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testUpdateBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Job job;
        final Future<HttpResponse<Buffer>> updateJob;

        try {
            job = TestUtils.getRandomJob(myInstitutionID);
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

        updateJob = myWebClient.put(JOB.expandToString(TestUtils.getUriTemplateVars(1))).sendJson(job.toJson());

        updateJob.onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#removeJob} before {@link Op#addJob} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testRemoveBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.delete(JOB.expandToString(TestUtils.getUriTemplateVars(1))).send().onSuccess(response -> {
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
     */
    @Test
    void testAddInvalidJob(final Vertx aVertx, final VertxTestContext aContext) {
        final Job validJob;
        final JsonObject invalidJobJson;

        try {
            validJob = TestUtils.getRandomJob(myInstitutionID);
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

        invalidJobJson = validJob.toJson().put(Job.INSTITUTION_ID, null);

        myWebClient.post(JOBS).sendJson(invalidJobJson).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#updateJob} with invalid JSON, after a successful {@link Op#addJob}, results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testUpdateInvalidJobAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Job validJob;
        final Future<HttpResponse<Buffer>> addJob;

        try {
            validJob = TestUtils.getRandomJob(myInstitutionID);
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJob = myWebClient.post(JOBS).expect(ResponsePredicate.JSON).sendJson(validJob.toJson());

        addJob.compose(addJobResponse -> {
            final Job responseJob = new Job(addJobResponse.bodyAsJsonObject());
            final Variables jobID;
            final JsonObject invalidJobJson;
            final Future<HttpResponse<Buffer>> updateJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobResponse.statusCode());
                assertTrue(responseJob.getID().isPresent());

                responseVerified.flag();
            });

            // Second request
            jobID = TestUtils.getUriTemplateVars(responseJob.getID().get());
            invalidJobJson = validJob.toJson().put(Job.INSTITUTION_ID, null);
            updateJob = myWebClient.put(JOB.expandToString(jobID)).sendJson(invalidJobJson);

            return updateJob.compose(updateJobResponse -> {
                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_BAD_REQUEST, updateJobResponse.statusCode());

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }
}
