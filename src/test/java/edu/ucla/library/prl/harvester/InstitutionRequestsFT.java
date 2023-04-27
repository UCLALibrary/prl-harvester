
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.mail.internet.AddressException;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * Tests the application's behavior in response to requests involving {@link Institution}s.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class InstitutionRequestsFT extends AuthorizedFIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstitutionRequestsFT.class, MessageCodes.BUNDLE);

    private static final UriTemplate INSTITUTION = UriTemplate.of("/institutions/{id}");

    private static final UriTemplate INSTITUTIONS = UriTemplate.of("/institutions");

    private Pool myDbConnectionPool;

    private JavaAsyncSolrClient mySolrClient;

    private WebClient myWebClient;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final String host = config.getString(Config.HTTP_HOST);
            final int port = Config.getHttpPort(config);
            final WebClientOptions webClientOpts = new WebClientOptions().setDefaultHost(host).setDefaultPort(port);

            myDbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            mySolrClient = JavaAsyncSolrClient.create(config.getString(Config.SOLR_CORE_URL));
            myWebClient = WebClientSession.create(WebClient.create(aVertx, webClientOpts));

            return authorize(myWebClient);
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
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
     * Tests that {@link Op#listInstitutions} initially retrieves an empty list.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testListBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.get(INSTITUTIONS).send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_OK, response.statusCode());
                assertTrue(response.bodyAsJsonArray().isEmpty());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#listInstitutions} after {@link Op#addInstitutions} retrieves a non-empty list.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testListAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Checkpoint solrVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Institution institution1;
        final Institution institution2;
        final Future<HttpResponse<Buffer>> addInstitutions;

        try {
            institution1 = TestUtils.getRandomInstitution();
            institution2 = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addInstitutions = myWebClient.post(INSTITUTIONS).expect(ResponsePredicate.JSON)
                .sendJson(new JsonArray().add(institution1.toJson()).add(institution2.toJson()));

        addInstitutions.compose(addInstitutionsResponse -> {
            final Set<Institution> firstResponseInstitutions =
                    institutionsFromJsonArray(addInstitutionsResponse.bodyAsJsonArray());
            final Future<HttpResponse<Buffer>> listInstitutions;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addInstitutionsResponse.statusCode());
                firstResponseInstitutions.forEach(responseInstitution -> {
                    assertTrue(responseInstitution.getID().isPresent());
                });

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.of(firstResponseInstitutions))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            solrVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool,
                    Optional.of(Set.of(institution1, institution2))).onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            dbVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            // Second request
            listInstitutions = myWebClient.get(INSTITUTIONS).expect(ResponsePredicate.JSON).send();

            return listInstitutions.compose(listInstitutionsResponse -> {
                final Set<Institution> secondResponseInstitutions =
                        institutionsFromJsonArray(listInstitutionsResponse.bodyAsJsonArray());

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, listInstitutionsResponse.statusCode());
                    assertEquals(firstResponseInstitutions, secondResponseInstitutions);

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getInstitution} after {@link Op#addInstitutions} retrieves the same data that was sent.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Checkpoint solrVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Institution institution;
        final Future<HttpResponse<Buffer>> addInstitutions;

        try {
            institution = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addInstitutions = myWebClient.post(INSTITUTIONS).expect(ResponsePredicate.JSON)
                .sendJson(new JsonArray().add(institution.toJson()));

        addInstitutions.compose(addInstitutionsResponse -> {
            final Institution firstResponseInstitution =
                    new Institution(addInstitutionsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables institutionID;
            final Future<HttpResponse<Buffer>> getInstitution;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addInstitutionsResponse.statusCode());
                assertTrue(firstResponseInstitution.getID().isPresent());

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.of(Set.of(firstResponseInstitution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            solrVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.of(Set.of(institution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            dbVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            // Second request
            institutionID = TestUtils.getUriTemplateVars(firstResponseInstitution.getID().get());
            getInstitution =
                    myWebClient.get(INSTITUTION.expandToString(institutionID)).expect(ResponsePredicate.JSON).send();

            return getInstitution.compose(getInstitutionResponse -> {
                final Institution secondResponseInstitution =
                        new Institution(getInstitutionResponse.bodyAsJsonObject());

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, getInstitutionResponse.statusCode());
                    assertEquals(firstResponseInstitution, secondResponseInstitution);

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getInstitution} after {@link Op#updateInstitution} retrieves different data than was sent in
     * the initial {@link Op#addInstitutions}.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterUpdateAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(3);
        final Checkpoint solrVerified = aContext.checkpoint(2);
        final Checkpoint dbVerified = aContext.checkpoint(2);
        final Institution institution;
        final Institution updatedInstitution;
        final Future<HttpResponse<Buffer>> addInstitutions;

        try {
            institution = TestUtils.getRandomInstitution();
            updatedInstitution = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addInstitutions = myWebClient.post(INSTITUTIONS).expect(ResponsePredicate.JSON)
                .sendJson(new JsonArray().add(institution.toJson()));

        addInstitutions.compose(addInstitutionsResponse -> {
            final Institution firstResponseInstitution =
                    new Institution(addInstitutionsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables institutionID;
            final Future<HttpResponse<Buffer>> updateInstitution;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addInstitutionsResponse.statusCode());
                assertTrue(firstResponseInstitution.getID().isPresent());

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.of(Set.of(firstResponseInstitution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            solrVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.of(Set.of(institution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            dbVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            // Second request
            institutionID = TestUtils.getUriTemplateVars(firstResponseInstitution.getID().get());
            updateInstitution = myWebClient.put(INSTITUTION.expandToString(institutionID))
                    .expect(ResponsePredicate.JSON).sendJson(updatedInstitution.toJson());

            return updateInstitution.compose(updateInstitutionResponse -> {
                final Institution secondResponseInstitution =
                        new Institution(updateInstitutionResponse.bodyAsJsonObject());
                final Future<HttpResponse<Buffer>> getInstitution;

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_OK, updateInstitutionResponse.statusCode());
                    assertNotEquals(firstResponseInstitution, secondResponseInstitution);

                    responseVerified.flag();
                });

                TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.of(Set.of(secondResponseInstitution)))
                        .onSuccess(assertions -> {
                            aContext.verify(() -> {
                                assertions.run();

                                solrVerified.flag();
                            });
                        }).onFailure(aContext::failNow);

                TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.of(Set.of(updatedInstitution)))
                        .onSuccess(assertions -> {
                            aContext.verify(() -> {
                                assertions.run();

                                dbVerified.flag();
                            });
                        }).onFailure(aContext::failNow);

                // Third request
                getInstitution = myWebClient.get(INSTITUTION.expandToString(institutionID))
                        .expect(ResponsePredicate.JSON).send();

                return getInstitution.compose(getInstitutionResponse -> {
                    final Institution thirdResponseInstitution =
                            new Institution(getInstitutionResponse.bodyAsJsonObject());

                    aContext.verify(() -> {
                        assertEquals(HttpStatus.SC_OK, getInstitutionResponse.statusCode());
                        assertEquals(secondResponseInstitution, thirdResponseInstitution);

                        responseVerified.flag();
                    });

                    return Future.succeededFuture();
                });
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getInstitution} after {@link Op#removeInstitution} after {@link Op#addInstitutions} results
     * in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetAfterRemoveAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(3);
        final Checkpoint solrVerified = aContext.checkpoint(2);
        final Checkpoint dbVerified = aContext.checkpoint(2);
        final Institution institution;
        final Future<HttpResponse<Buffer>> addInstitutions;

        try {
            institution = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addInstitutions = myWebClient.post(INSTITUTIONS).expect(ResponsePredicate.JSON)
                .sendJson(new JsonArray().add(institution.toJson()));

        addInstitutions.compose(addInstitutionsResponse -> {
            final Institution firstResponseInstitution =
                    new Institution(addInstitutionsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables institutionID;
            final Future<HttpResponse<Buffer>> removeInstitution;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addInstitutionsResponse.statusCode());
                assertTrue(firstResponseInstitution.getID().isPresent());

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.of(Set.of(firstResponseInstitution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            solrVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.of(Set.of(institution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            dbVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            // Second request
            institutionID = TestUtils.getUriTemplateVars(firstResponseInstitution.getID().get());
            removeInstitution = myWebClient.delete(INSTITUTION.expandToString(institutionID)).send();

            return removeInstitution.compose(removeInstitutionResponse -> {
                final Future<HttpResponse<Buffer>> getInstitution;

                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_NO_CONTENT, removeInstitutionResponse.statusCode());

                    responseVerified.flag();
                });

                TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.empty()).onSuccess(assertions -> {
                    aContext.verify(() -> {
                        assertions.run();

                        solrVerified.flag();
                    });
                }).onFailure(aContext::failNow);

                TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.empty())
                        .onSuccess(assertions -> {
                            aContext.verify(() -> {
                                assertions.run();

                                dbVerified.flag();
                            });
                        }).onFailure(aContext::failNow);

                // Third request
                getInstitution = myWebClient.get(INSTITUTION.expandToString(institutionID)).send();

                return getInstitution;
            }).compose(getInstitutionResponse -> {
                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_NOT_FOUND, getInstitutionResponse.statusCode());

                    responseVerified.flag();
                });

                return Future.succeededFuture();
            });
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#getInstitution} before {@link Op#addInstitutions} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testGetBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.get(INSTITUTION.expandToString(TestUtils.getUriTemplateVars(1))).send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#updateInstitution} before {@link Op#addInstitutions} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testUpdateBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint solrVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Institution institution;
        final Future<HttpResponse<Buffer>> updateInstitution;

        try {
            institution = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        updateInstitution = myWebClient.put(INSTITUTION.expandToString(TestUtils.getUriTemplateVars(1)))
                .sendJson(institution.toJson());

        updateInstitution.onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    solrVerified.flag();
                });
            }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#removeInstitution} before {@link Op#addInstitutions} results in HTTP 404.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testRemoveBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.delete(INSTITUTION.expandToString(TestUtils.getUriTemplateVars(1))).send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#addInstitutions} with an empty JSON array results in HTTP 400.
     *
     * @param aVertx
     * @param aContext
     */
    @Test
    void testAddInstitutionsEmptyList(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint solrVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();

        myWebClient.post(INSTITUTIONS).sendJson(new JsonArray()).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
                assertEquals(LOGGER.getMessage(MessageCodes.PRL_047), response.bodyAsString());

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    solrVerified.flag();
                });
            }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#addInstitutions} with invalid JSON results in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testAddInstitutionsInvalidJSON(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint();
        final Checkpoint solrVerified = aContext.checkpoint();
        final Checkpoint dbVerified = aContext.checkpoint();
        final Institution validInstitution;
        final JsonObject invalidInstitutionJson;

        try {
            validInstitution = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        invalidInstitutionJson = validInstitution.toJson();
        invalidInstitutionJson.remove(Institution.NAME);

        myWebClient.post(INSTITUTIONS).sendJson(new JsonArray().add(invalidInstitutionJson)).onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_BAD_REQUEST, response.statusCode());
                assertEquals(LOGGER.getMessage(MessageCodes.PRL_039, Institution.NAME), response.bodyAsString());

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    solrVerified.flag();
                });
            }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.empty()).onSuccess(assertions -> {
                aContext.verify(() -> {
                    assertions.run();

                    dbVerified.flag();
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#updateInstitution} with invalid JSON, after a successful {@link Op#addInstitutions}, results
     * in HTTP 400.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testUpdateInstitutionInvalidJsonAfterAdd(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint responseVerified = aContext.checkpoint(2);
        final Checkpoint solrVerified = aContext.checkpoint(2);
        final Checkpoint dbVerified = aContext.checkpoint(2);
        final Institution validInstitution;
        final Future<HttpResponse<Buffer>> addInstitutions;

        try {
            validInstitution = TestUtils.getRandomInstitution();
        } catch (final AddressException | MalformedURLException | NumberParseException details) {
            aContext.failNow(details);
            return;
        }

        // First request
        addInstitutions = myWebClient.post(INSTITUTIONS).expect(ResponsePredicate.JSON)
                .sendJson(new JsonArray().add(validInstitution.toJson()));

        addInstitutions.compose(addInstitutionsResponse -> {
            final Institution firstResponseInstitution =
                    new Institution(addInstitutionsResponse.bodyAsJsonArray().getJsonObject(0));
            final Variables institutionID;
            final JsonObject invalidInstitutionJson;
            final Future<HttpResponse<Buffer>> updateInstitution;

            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_CREATED, addInstitutionsResponse.statusCode());
                assertTrue(firstResponseInstitution.getID().isPresent());

                responseVerified.flag();
            });

            TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.of(Set.of(firstResponseInstitution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            solrVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.of(Set.of(validInstitution)))
                    .onSuccess(assertions -> {
                        aContext.verify(() -> {
                            assertions.run();

                            dbVerified.flag();
                        });
                    }).onFailure(aContext::failNow);

            // Second request
            institutionID = TestUtils.getUriTemplateVars(firstResponseInstitution.getID().get());

            invalidInstitutionJson = validInstitution.toJson();
            invalidInstitutionJson.remove(Institution.NAME);

            updateInstitution =
                    myWebClient.put(INSTITUTION.expandToString(institutionID)).sendJson(invalidInstitutionJson);

            return updateInstitution.compose(updateInstitutionResponse -> {
                aContext.verify(() -> {
                    assertEquals(HttpStatus.SC_BAD_REQUEST, updateInstitutionResponse.statusCode());
                    assertEquals(LOGGER.getMessage(MessageCodes.PRL_039, Institution.NAME),
                            updateInstitutionResponse.bodyAsString());

                    responseVerified.flag();

                });

                TestUtils.getSolrInstitutionAssertions(mySolrClient, Optional.of(Set.of(firstResponseInstitution)))
                        .onSuccess(assertions -> {
                            aContext.verify(() -> {
                                assertions.run();

                                solrVerified.flag();
                            });
                        }).onFailure(aContext::failNow);

                TestUtils.getDatabaseInstitutionAssertions(myDbConnectionPool, Optional.of(Set.of(validInstitution)))
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
     * @param anArray A JSON array
     * @return The set of {@link Institution}s represented by the array
     */
    private static Set<Institution> institutionsFromJsonArray(final JsonArray anArray) {
        return anArray.stream().map(entry -> new Institution(JsonObject.mapFrom(entry))).collect(Collectors.toSet());
    }
}
