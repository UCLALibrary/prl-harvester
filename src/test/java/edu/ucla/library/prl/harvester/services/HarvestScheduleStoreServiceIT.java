
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.ucla.library.prl.harvester.Error;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.MessageCodes;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

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
        final String myName = "Acme Looniversity";
        final String myDescription = "Wacky University";
        final String myLocation = "Acme Acres";
        final Optional<InternetAddress> myEmail = Optional.of(new InternetAddress("bugs@ebunny.com"));
        final Optional<PhoneNumber> myPhone = Optional.of(PHONE_NUMBER_UTIL.parse("+1 888 200 1000", null));
        final Optional<URL> myContact = Optional.of(new URL("http://acme.edu/1/contact"));
        final URL myWebsite = new URL("http://acme.edu/1");
        final Institution inst =
                new Institution(myName, myDescription, myLocation, myEmail, myPhone, myContact, myWebsite);
        myScheduleStoreProxy.addInstitution(inst).onSuccess(result -> {
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
            aContext.failNow("this shouldn't happen");
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
        final int instID = 1;
        myScheduleStoreProxy.removeInstitution(instID).onSuccess(result -> {
            myScheduleStoreProxy.getInstitution(instID).onFailure(details -> {
                final ServiceException error = (ServiceException) details;

                aContext.verify(() -> {
                    assertEquals(Error.NOT_FOUND.ordinal(), error.failureCode());
                    assertTrue(error.getMessage().contains(String.valueOf(instID)));

                    aContext.completeNow();
                });
            }).onSuccess(select -> {
                aContext.failNow("delete failed");
            });
        }).onFailure(aContext::failNow);
    }

}