
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.mail.internet.AddressException;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.i18n.phonenumbers.NumberParseException;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import io.ino.solrs.JavaAsyncSolrClient;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
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
public class JobRequestsFT extends AuthorizedFIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobRequestsFT.class, MessageCodes.BUNDLE);

    private static final UriTemplate INSTITUTIONS = UriTemplate.of("/institutions");

    private static final UriTemplate JOB = UriTemplate.of("/jobs/{id}");

    private static final UriTemplate JOBS = UriTemplate.of("/jobs");

    private Pool myDbConnectionPool;

    private JavaAsyncSolrClient mySolrClient;

    private URL myTestProviderBaseURL;

    private WebClient myWebClient;

    private int myInstitutionID;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final String host = config.getString(TestUtils.HTTP_HOST);
            final int port = Config.getHttpPort(config);
            final WebClientOptions webClientOpts = new WebClientOptions().setDefaultHost(host).setDefaultPort(port);

            myDbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            mySolrClient = JavaAsyncSolrClient.create(config.getString(Config.SOLR_CORE_URL));

            try {
                myTestProviderBaseURL = new URL(config.getString(TestUtils.TEST_PROVIDER_BASE_URL));
            } catch (final MalformedURLException details) {
                return Future.failedFuture(details);
            }

            myWebClient = WebClientSession.create(WebClient.create(aVertx, webClientOpts));

            return authorize(myWebClient);
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

        myWebClient.post(INSTITUTIONS).sendJson(new JsonArray().add(institution.toJson())).onSuccess(response -> {
            myInstitutionID = new Institution(response.bodyAsJsonArray().getJsonObject(0)).getID().get();

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @param aTestInfo Information about the current test
     */
    @AfterEach
    public void afterEach(final Vertx aVertx, final VertxTestContext aContext, final TestInfo aTestInfo) {
        TestUtils.getAllDocuments(mySolrClient).compose(result -> {
            LOGGER.info(MessageCodes.PRL_037, aTestInfo.getDisplayName(), result.toString());

            return TestUtils.resetApplication(myWebClient);
        }).onSuccess(nil -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public final void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.close();
        mySolrClient.shutdown();
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
     * Tests that {@link Op#listJobs} after {@link Op#addJobs} retrieves a non-empty list.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testListAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Checkpoint dbVerified = aContext.checkpoint();
        final Job job1;
        final Job job2;
        final Future<HttpResponse<Buffer>> addJobs;

        try {
            job1 = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET1));
            job2 = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET2));
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJobs = myWebClient.post(JOBS).sendJson(new JsonArray().add(job1.toJson()).add(job2.toJson()));

        addJobs.compose(addJobsResponse -> {
            final Set<Job> firstResponseJobs = jobsFromJsonArray(addJobsResponse.bodyAsJsonArray());
            final Future<HttpResponse<Buffer>> listJobs;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobsResponse.statusCode());
                firstResponseJobs.forEach(job -> {
                    assertTrue(job.getID().isPresent());
                });

                responseVerified.flag();
            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.of(Set.of(job1, job2)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            dbVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            // Second request
            listJobs = myWebClient.get(JOBS).expect(ResponsePredicate.JSON).send();

            return listJobs.compose(listJobsResponse -> {
                final Set<Job> secondResponseJobs = jobsFromJsonArray(listJobsResponse.bodyAsJsonArray());

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, listJobsResponse.statusCode());
                    assertEquals(firstResponseJobs, secondResponseJobs);

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getJob} after {@link Op#addJobs} retrieves the same data that was sent.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Checkpoint dbVerified = aContext.checkpoint();
        final Job job;
        final Future<HttpResponse<Buffer>> addJobs;

        try {
            job = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET1));
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJobs = myWebClient.post(JOBS).sendJson(new JsonArray().add(job.toJson()));

        addJobs.compose(addJobsResponse -> {
            final Job firstResponseJob = new Job(addJobsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables jobID;
            final Future<HttpResponse<Buffer>> getJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobsResponse.statusCode());
                assertTrue(firstResponseJob.getID().isPresent());

                responseVerified.flag();
            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.of(Set.of(job))).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);

            // Second request
            jobID = TestUtils.getUriTemplateVars(firstResponseJob.getID().get());
            getJob = myWebClient.get(JOB.expandToString(jobID)).expect(ResponsePredicate.JSON).send();

            return getJob.compose(getJobResponse -> {
                final Job secondResponseJob = new Job(getJobResponse.bodyAsJsonObject());

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, getJobResponse.statusCode());
                    assertEquals(firstResponseJob, secondResponseJob);

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getJob} after {@link Op#updateJob} retrieves different data than was sent in the initial
     * {@link Op#addJobs}.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterUpdateAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(3);
        final Checkpoint dbVerified = aContext.checkpoint(2);
        final Job job;
        final Job updatedJob;
        final Future<HttpResponse<Buffer>> addJobs;

        try {
            job = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET2));
            updatedJob =
                    TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET1, TestUtils.SET2));
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJobs = myWebClient.post(JOBS).expect(ResponsePredicate.JSON).sendJson(new JsonArray().add(job.toJson()));

        addJobs.compose(addJobsResponse -> {
            final Job firstResponseJob = new Job(addJobsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables jobID;
            final Future<HttpResponse<Buffer>> updateJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobsResponse.statusCode());
                assertTrue(firstResponseJob.getID().isPresent());

                responseVerified.flag();
            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.of(Set.of(job))).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);

            // Second request
            jobID = TestUtils.getUriTemplateVars(firstResponseJob.getID().get());
            updateJob = myWebClient.put(JOB.expandToString(jobID)).expect(ResponsePredicate.JSON)
                    .sendJson(updatedJob.toJson());

            return updateJob.compose(updateJobResponse -> {
                final Job secondResponseJob = new Job(updateJobResponse.bodyAsJsonObject());
                final Future<HttpResponse<Buffer>> getJob;

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, updateJobResponse.statusCode());
                    assertNotEquals(firstResponseJob, secondResponseJob);

                    responseVerified.flag();
                });

                TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.of(Set.of(updatedJob)))
                        .onSuccess(assertions -> {
                            aContext.verify(() -> {
                                assertions.run();

                                dbVerified.flag();
                            });
                        }).onFailure(aContext::failNow);

                // Third request
                getJob = myWebClient.get(JOB.expandToString(jobID)).expect(ResponsePredicate.JSON).send();

                return getJob.compose(getJobResponse -> {
                    final Job thirdResponseJob = new Job(getJobResponse.bodyAsJsonObject());

                    aContext.verify(() -> {
                        assertEquals(HttpStatus.SC_OK, getJobResponse.statusCode());
                        assertEquals(secondResponseJob, thirdResponseJob);

                        responseVerified.flag();
                    });

                    return Future.succeededFuture();
                });
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getJob} after {@link Op#removeJob} after {@link Op#addJobs} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterRemoveAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(3);
        final Checkpoint dbVerified = aContext.checkpoint(2);
        final Job job;
        final Future<HttpResponse<Buffer>> addJobs;

        try {
            job = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET1));
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJobs = myWebClient.post(JOBS).expect(ResponsePredicate.JSON).sendJson(new JsonArray().add(job.toJson()));

        addJobs.compose(addJobsResponse -> {
            final Job firstResponseJob = new Job(addJobsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables jobID;
            final Future<HttpResponse<Buffer>> removeJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobsResponse.statusCode());
                assertTrue(firstResponseJob.getID().isPresent());

                responseVerified.flag();
            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.of(Set.of(job))).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);

            // Second request
            jobID = TestUtils.getUriTemplateVars(firstResponseJob.getID().get());
            removeJob = myWebClient.delete(JOB.expandToString(jobID)).send();

            return removeJob.compose(removeJobResponse -> {
                final Future<HttpResponse<Buffer>> getJob;

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_NO_CONTENT, removeJobResponse.statusCode());

                    responseVerified.flag();
                });

                TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                    aContext.verify(() -> {
                        assertions.run();

                        dbVerified.flag();
                    });
                }).onFailure(aContext::failNow);

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
     * Tests that {@link Op#getJob} before {@link Op#addJobs} results in HTTP 404.
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
     * Tests that {@link Op#updateJob} before {@link Op#addJobs} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testUpdateBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Job job;
        final Future<HttpResponse<Buffer>> updateJob;

        try {
            job = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET2));
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        updateJob = myWebClient.put(JOB.expandToString(TestUtils.getUriTemplateVars(1))).sendJson(job.toJson());

        updateJob.onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());

                responseVerified.flag();
            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#removeJob} before {@link Op#addJobs} results in HTTP 404.
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
     * Tests that {@link Op#addJobs} with an empty JSON array results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testAddJobsEmptyList(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();

        myWebClient.post(JOBS).sendJson(new JsonArray()).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
                assertEquals(LOGGER.getMessage(MessageCodes.PRL_047), response.bodyAsString());

                responseVerified.flag();

            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#addJobs} with invalid JSON results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testAddJobsInvalidJSON(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Job validJob;
        final JsonObject invalidJobJson;

        try {
            validJob = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of());
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        invalidJobJson = validJob.toJson();
        invalidJobJson.remove(Job.INSTITUTION_ID);

        myWebClient.post(JOBS).sendJson(new JsonArray().add(invalidJobJson)).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
                assertEquals(LOGGER.getMessage(MessageCodes.PRL_039, Job.INSTITUTION_ID), response.bodyAsString());

                responseVerified.flag();

            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#updateJob} with invalid JSON, after a successful {@link Op#addJobs}, results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testUpdateJobInvalidJsonAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Checkpoint dbVerified = aContext.checkpoint(2);
        final Job validJob;
        final Future<HttpResponse<Buffer>> addJobs;

        try {
            validJob =
                    TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of(TestUtils.SET1, TestUtils.SET2));
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addJobs =
                myWebClient.post(JOBS).expect(ResponsePredicate.JSON).sendJson(new JsonArray().add(validJob.toJson()));

        addJobs.compose(addJobsResponse -> {
            final Job firstResponseJob = new Job(addJobsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables jobID;
            final JsonObject invalidJobJson;
            final Future<HttpResponse<Buffer>> updateJob;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addJobsResponse.statusCode());
                assertTrue(firstResponseJob.getID().isPresent());

                responseVerified.flag();

            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.of(Set.of(validJob)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            dbVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            // Second request
            jobID = TestUtils.getUriTemplateVars(firstResponseJob.getID().get());

            invalidJobJson = validJob.toJson();
            invalidJobJson.remove(Job.INSTITUTION_ID);

            updateJob = myWebClient.put(JOB.expandToString(jobID)).sendJson(invalidJobJson);

            return updateJob.compose(updateJobResponse -> {
                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_BAD_REQUEST, updateJobResponse.statusCode());
                    assertEquals(LOGGER.getMessage(MessageCodes.PRL_039, Job.INSTITUTION_ID),
                            updateJobResponse.bodyAsString());

                    responseVerified.flag();

                });

                TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.of(Set.of(validJob)))
                        .onSuccess(assertions -> {
                            aContext.verify(() -> {
                                assertions.run();

                                dbVerified.flag();
                            });
                        }).onFailure(aContext::failNow);

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#addJobs} with an invalid OAI-PMH base URL results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testAddJobInvalidBaseURL(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Job invalidJob;

        try {
            invalidJob = TestUtils.getJob(myInstitutionID, new URL("http://example.com"), List.of());
        } catch (final MalformedURLException | ParseException details) {
            aContext.failNow(details);
            return;
        }

        myWebClient.post(JOBS).sendJson(new JsonArray().add(invalidJob.toJson())).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());

                responseVerified.flag();

            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#addJobs} with one or more undefined OAI-PMH sets results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testAddJobInvalidSets(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Job invalidJob;

        try {
            invalidJob = TestUtils.getJob(myInstitutionID, myTestProviderBaseURL, List.of("set3"));
        } catch (final ParseException details) {
            aContext.failNow(details);
            return;
        }

        myWebClient.post(JOBS).sendJson(new JsonArray().add(invalidJob.toJson())).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());

                responseVerified.flag();

            });

            TestUtils.getDatabaseJobAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * @param anArray A JSON array
     * @return The set of {@link Job}s represented by the array
     */
    private static Set<Job> jobsFromJsonArray(final JsonArray anArray) {
        return anArray.stream().map(entry -> new Job(JsonObject.mapFrom(entry))).collect(Collectors.toSet());
    }
}
