
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.ucla.library.prl.harvester.Error;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.ZonedDateTime;

import javax.mail.internet.AddressException;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.serviceproxy.ServiceException;

/**
 * Tests {@linkHarvestScheduleStoreService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class HarvestScheduleStoreServiceIT {

    /**
     * The schedule store test's logger.
     */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(HarvestScheduleStoreServiceIT.class, MessageCodes.BUNDLE);

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private static final String SAMPLE_NAME = "Sample 1";

    private static final String SAMPLE_CRON = "0 0/30 8-9 5,20 * ?";

    private static final String UPDATE_URL = "http://new.url.com";

    private MessageConsumer<?> myHarvestScheduleStoreService;

    private HarvestScheduleStoreService myScheduleStoreProxy;

    /**
     * Sets up the test service.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        final ConfigRetriever retriever = ConfigRetriever.create(aVertx);

        retriever.getConfig().onSuccess(config -> {
            final HarvestScheduleStoreService service = HarvestScheduleStoreService.create(aVertx, config);
            final ServiceBinder binder = new ServiceBinder(aVertx);

            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, service);
            myScheduleStoreProxy = HarvestScheduleStoreService.createProxy(aVertx);

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Clean up the service client.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public final void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        myScheduleStoreProxy.close().compose(result -> myHarvestScheduleStoreService.unregister())
                .onSuccess(success -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Tests inserting institution record in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testAddInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final Institution toAdd = TestUtils.getRandomInstitution();
        myScheduleStoreProxy.addInstitution(toAdd).onSuccess(result -> {
            aContext.verify(() -> {
                assertTrue(result.intValue() >= 1);
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests getting institution by ID from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int instID = 1;
        myScheduleStoreProxy.getInstitution(instID).onSuccess(institution -> {
            aContext.verify(() -> {
                assertTrue(institution != null);
                assertTrue(institution.getName().equals(SAMPLE_NAME));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests handling bad institution ID in get institution.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetInstitutionBadID(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int badID = -1;
        myScheduleStoreProxy.getInstitution(badID).onFailure(details -> {
            final ServiceException error = (ServiceException) details;

            aContext.verify(() -> {
                assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                assertTrue(error.getMessage().contains(String.valueOf(badID)));

                aContext.completeNow();
            });
        }).onSuccess(result -> {
            aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_011, badID));
        });
    }

    /**
     * Tests getting all institutions from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testListInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        myScheduleStoreProxy.listInstitutions().onSuccess(instList -> {
            aContext.verify(() -> {
                assertTrue(instList != null);
                assertTrue(instList.size() >= 3);
                assertTrue(instList.get(0).getName().equals(SAMPLE_NAME));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests deleting institution from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testDeleteInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final Institution toDelete = TestUtils.getRandomInstitution();
        myScheduleStoreProxy.addInstitution(toDelete).onSuccess(newID -> {
            myScheduleStoreProxy.removeInstitution(newID).onSuccess(result -> {
                myScheduleStoreProxy.getInstitution(newID).onFailure(details -> {
                    final ServiceException error = (ServiceException) details;

                    aContext.verify(() -> {
                        assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                        assertTrue(error.getMessage().contains(String.valueOf(newID)));
                    }).completeNow();
                }).onSuccess(select -> {
                    aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_013, newID));
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests updating institution in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testUpdateInstitution(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int instID = 1;
        myScheduleStoreProxy.getInstitution(instID).onSuccess(original -> {
            final Institution modified =
                    new Institution(original.getName(), "changing description", "changing location",
                            original.getEmail(), original.getPhone(), original.getWebContact(), original.getWebsite());
            myScheduleStoreProxy.updateInstitution(instID, modified).onSuccess(result -> {
                myScheduleStoreProxy.getInstitution(instID).onSuccess(updated -> {
                    aContext.verify(() -> {
                        assertTrue(updated.getName().equals(original.getName()));
                        assertTrue(updated.getDescription().equals(modified.getDescription()));
                    }).completeNow();
                }).onFailure(aContext::failNow);
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests getting job by ID from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int jobID = 1;
        myScheduleStoreProxy.getJob(jobID).onSuccess(job -> {
            aContext.verify(() -> {
                assertTrue(job != null);
                assertTrue(job.getScheduleCronExpression().toString().equals(SAMPLE_CRON));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests handling bad job ID in get job.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testGetJobBadID(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int badID = -1;
        myScheduleStoreProxy.getJob(badID).onFailure(details -> {
            final ServiceException error = (ServiceException) details;

            aContext.verify(() -> {
                assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                assertTrue(error.getMessage().contains(String.valueOf(badID)));

                aContext.completeNow();
            });
        }).onSuccess(result -> {
            aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_012, badID));
        });
    }

    /**
     * Tests inserting job record in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testAddJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException, ParseException {
        final Job toAdd = TestUtils.getRandomJob();
        myScheduleStoreProxy.addJob(toAdd).onSuccess(result -> {
            aContext.verify(() -> {
                assertTrue(result.intValue() >= 1);
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests getting all jobs from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testListJobs(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        myScheduleStoreProxy.listJobs().onSuccess(jobList -> {
            aContext.verify(() -> {
                assertTrue(jobList != null);
                assertTrue(jobList.size() >= 3);
                assertTrue(jobList.get(0).getScheduleCronExpression().toString().equals(SAMPLE_CRON));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests deleting job from db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testDeleteJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException, ParseException {
        final Job toDelete = TestUtils.getRandomJob();
        myScheduleStoreProxy.addJob(toDelete).onSuccess(newID -> {
            myScheduleStoreProxy.removeJob(newID).onSuccess(result -> {
                myScheduleStoreProxy.getJob(newID).onFailure(details -> {
                    final ServiceException error = (ServiceException) details;

                    aContext.verify(() -> {
                        assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                        assertTrue(error.getMessage().contains(String.valueOf(newID)));
                    }).completeNow();
                }).onSuccess(select -> {
                    aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_014, newID));
                });
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests updating job in db.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testUpdateJob(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int jobID = 1;
        myScheduleStoreProxy.getJob(jobID).onSuccess(original -> {
            try {
                final Job modified = new Job(original.getInstitutionID(), new URL(UPDATE_URL),
                        original.getSets().get(), original.getScheduleCronExpression(), ZonedDateTime.now());
                myScheduleStoreProxy.updateJob(jobID, modified).onSuccess(result -> {
                    myScheduleStoreProxy.getJob(jobID).onSuccess(updated -> {
                        aContext.verify(() -> {
                            assertTrue(updated.getInstitutionID() == original.getInstitutionID());
                            assertTrue(updated.getRepositoryBaseURL().equals(modified.getRepositoryBaseURL()));
                        }).completeNow();
                    }).onFailure(aContext::failNow);
                }).onFailure(aContext::failNow);
            } catch (MalformedURLException details) {
                aContext.failNow(details);
            }
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests updating job in db with bad institution ID.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testUpdateJobBadID(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        final int jobID = 1;
        final int badInstID = -1;
        myScheduleStoreProxy.getJob(jobID).onSuccess(original -> {
            try {
                final Job modified = new Job(badInstID, new URL(UPDATE_URL), original.getSets().get(),
                        original.getScheduleCronExpression(), ZonedDateTime.now());
                myScheduleStoreProxy.updateJob(jobID, modified).onSuccess(result -> {
                    myScheduleStoreProxy.getJob(jobID).onSuccess(updated -> {
                        aContext.verify(() -> {
                            assertTrue(updated.getInstitutionID() == original.getInstitutionID());
                            assertFalse(updated.getRepositoryBaseURL().equals(modified.getRepositoryBaseURL()));
                        }).completeNow();
                    }).onFailure(aContext::failNow);
                }).onFailure(aContext::failNow);
            } catch (MalformedURLException details) {
                aContext.failNow(details);
            }
        }).onFailure(aContext::failNow);
    }

}
