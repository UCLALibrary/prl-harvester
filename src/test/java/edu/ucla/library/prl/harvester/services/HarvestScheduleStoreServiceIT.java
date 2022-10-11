
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;

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

    private MessageConsumer<?> myHarvestScheduleStoreService;

    private HarvestScheduleStoreService myScheduleStoreProxy;

    /**
     * Sets up the test service.
     *
     * @param aVertx A A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        final ConfigRetriever cr = ConfigRetriever.create(aVertx);

        cr.getConfig().compose(config -> {
            return Future.succeededFuture(HarvestScheduleStoreService.create(aVertx, config));
        }).onSuccess(services -> {
            final ServiceBinder sb = new ServiceBinder(aVertx);

            myHarvestScheduleStoreService = sb.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, services);
            myScheduleStoreProxy = HarvestScheduleStoreService.createProxy(aVertx);

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Clean up the service client.
     *
     * @param aVertx A A Vert.x instance
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
     * @param aVertx A A Vert.x instance
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
            LOGGER.info("result ID = " + result);
            assertTrue(result != null);
            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }
}
