
package edu.ucla.library.prl.harvester.utils;

import edu.ucla.library.prl.harvester.Institution;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.jeasy.random.randomizers.EmailRandomizer;
import org.jeasy.random.randomizers.Ipv4AddressRandomizer;
import org.jeasy.random.randomizers.RegularExpressionRandomizer;
import org.jeasy.random.randomizers.SentenceRandomizer;

/**
 * Utilities related to working with test objects.
 */
public final class TestUtils {

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private static final String URL_PREFIX = "http://";

    private static final String CONTACT_POSTFIX = "/contact";

    private static final EmailRandomizer RAND_EMAIL = new EmailRandomizer();

    private static final Ipv4AddressRandomizer RAND_URL = new Ipv4AddressRandomizer();

    private static final RegularExpressionRandomizer RAND_PHONE =
            new RegularExpressionRandomizer("^\\+1 310 [2-9]\\d{2} \\d{4}$");

    private static final SentenceRandomizer RAND_STRING = new SentenceRandomizer();

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

}
