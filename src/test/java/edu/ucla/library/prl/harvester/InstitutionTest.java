
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import info.freelibrary.util.IllegalArgumentI18nException;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * Tests {@link Institution}.
 */
@Execution(ExecutionMode.CONCURRENT)
public class InstitutionTest {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(InstitutionTest.class, MessageCodes.BUNDLE);

    /**
     * Parses and formats phone numbers.
     */
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    /**
     * Tests that an {@link Institution} can be instantiated from a {@link JsonObject} and serialized back to one.
     *
     * @param aName The institution's name
     * @param aDescription The institution's description
     * @param aLocation The institution's human-readable location
     * @param anEmail The institution's optional email contact
     * @param aPhone The institution's optional phone contact
     * @param aWebContact The institution's optional web contact
     * @param aWebsite The institiution's website
     */
    @ParameterizedTest
    @MethodSource
    void testInstitutionSerDe(final String aName, final String aDescription, final String aLocation,
            final Optional<InternetAddress> anEmail, final Optional<PhoneNumber> aPhone,
            final Optional<URL> aWebContact, final URL aWebsite) {
        final Institution institution =
                new Institution(aName, aDescription, aLocation, anEmail, aPhone, aWebContact, aWebsite);
        final JsonObject json = new JsonObject() //
                .put(Institution.NAME, aName) //
                .put(Institution.DESCRIPTION, aDescription) //
                .put(Institution.LOCATION, aLocation) //
                .put(Institution.EMAIL, anEmail.map(InternetAddress::toString).orElse(null)) //
                .put(Institution.PHONE, aPhone //
                        .map(phone -> PHONE_NUMBER_UTIL.format(phone, PhoneNumberFormat.INTERNATIONAL)) //
                        .orElse(null)) //
                .put(Institution.WEB_CONTACT, aWebContact.map(URL::toString).orElse(null)) //
                .put(Institution.WEBSITE, aWebsite.toString());
        final Institution institutionFromJson = new Institution(json);

        // If the JSON representations are equal, then serialization works
        assertEquals(json, institution.toJson());
        assertEquals(institution.toJson(), institutionFromJson.toJson());

        // If the objects are equal, then deserialization works
        assertEquals(institution.getName(), institutionFromJson.getName());
        assertEquals(institution.getDescription(), institutionFromJson.getDescription());
        assertEquals(institution.getLocation(), institutionFromJson.getLocation());
        assertEquals(institution.getEmail(), institutionFromJson.getEmail());
        assertEquals(institution.getPhone(), institutionFromJson.getPhone());
        assertEquals(institution.getWebContact(), institutionFromJson.getWebContact());
        assertEquals(institution.getWebsite(), institutionFromJson.getWebsite());
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws AddressException
     * @throws MalformedURLException
     * @throws NumberParseException
     */
    static Stream<Arguments> testInstitutionSerDe()
            throws AddressException, MalformedURLException, NumberParseException {
        final String exampleName = "Name 1";
        final String exampleDescription = "Description 1";
        final String exampleLocation = "Location 1";
        final Optional<InternetAddress> exampleEmail = Optional.of(new InternetAddress("test0@example.com"));
        final Optional<PhoneNumber> examplePhone = Optional.of(PHONE_NUMBER_UTIL.parse("+1 888 200 1000", null));
        final Optional<URL> exampleWebContact = Optional.of(new URL("http://example.com/1/contact"));
        final URL exampleWebsite = new URL("http://example.com/1");

        return Stream.of( //
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, examplePhone,
                        exampleWebContact, exampleWebsite),
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, examplePhone,
                        Optional.empty(), exampleWebsite),
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, Optional.empty(),
                        exampleWebContact, exampleWebsite),
                Arguments.of(exampleName, exampleDescription, exampleLocation, Optional.empty(), examplePhone,
                        exampleWebContact, exampleWebsite),
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, Optional.empty(),
                        Optional.empty(), exampleWebsite),
                Arguments.of(exampleName, exampleDescription, exampleLocation, Optional.empty(), examplePhone,
                        Optional.empty(), exampleWebsite),
                Arguments.of(exampleName, exampleDescription, exampleLocation, Optional.empty(), Optional.empty(),
                        exampleWebContact, exampleWebsite));
    }

    /**
     * Tests that an {@link Institution} cannot be instantiated from an invalid JSON representation.
     *
     * @param aName The institution's name
     * @param aDescription The institution's description
     * @param aLocation The institution's human-readable location
     * @param anEmail The institution's optional email contact
     * @param aPhone The institution's optional phone contact
     * @param aWebContact The institution's optional web contact
     * @param aWebsite The institiution's website
     * @param anErrorClass The class of error that we expect instantiation with the above arguments to throw
     */
    @ParameterizedTest
    @MethodSource
    void testInstitutionInvalidJsonRepresentation(final String aName, final String aDescription, final String aLocation,
            final String anEmail, final String aPhone, final String aWebContact, final String aWebsite,
            final Class<Exception> anErrorClass) {
        final JsonObject json = new JsonObject() //
                .put(Institution.NAME, aName) //
                .put(Institution.DESCRIPTION, aDescription) //
                .put(Institution.LOCATION, aLocation) //
                .put(Institution.EMAIL, anEmail) //
                .put(Institution.PHONE, aPhone) //
                .put(Institution.WEB_CONTACT, aWebContact) //
                .put(Institution.WEBSITE, aWebsite);
        final Exception error = assertThrows(InvalidInstitutionJsonException.class, () -> new Institution(json));

        if (error.getCause() != null) {
            assertEquals(anErrorClass, error.getCause().getClass());
        }

        LOGGER.debug(LOGGER.getMessage(MessageCodes.PRL_000, error));
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws AddressException
     * @throws MalformedURLException
     * @throws NumberParseException
     */
    static Stream<Arguments> testInstitutionInvalidJsonRepresentation()
            throws AddressException, MalformedURLException, NumberParseException {
        final String validName = "Name 2";
        final String validDescription = "Description 2";
        final String validLocation = "Location 2";

        final String validEmail = new InternetAddress("test1@example.com", true).toString();
        final String invalidEmail = "@example.com"; // Missing username

        final String validPhone = PHONE_NUMBER_UTIL.format(PHONE_NUMBER_UTIL.parse("+1 888 200 2000", null),
                PhoneNumberFormat.INTERNATIONAL);
        final String invalidPhone1 = "888 200 2000"; // Missing country code
        final String invalidPhone2 = "911";

        final String validWebContact = new URL("http://example.com/2/contact").toString();
        final String invalidWebContact = "example.com/2/contact"; // Missing protocol

        final String validWebsite = new URL("http://example.com/2").toString();
        final String invalidWebsite = "example.com/2"; // Missing protocol

        return Stream.of( //
                Arguments.of(null, validDescription, validLocation, validEmail, validPhone, validWebContact,
                        validWebsite, null), //
                Arguments.of(validName, null, validLocation, validEmail, validPhone, validWebContact, validWebsite,
                        null), //
                Arguments.of(validName, validDescription, null, validEmail, validPhone, validWebContact, validWebsite,
                        null), //
                Arguments.of(validName, validDescription, validLocation, null, null, null, validWebsite, null), //
                Arguments.of(validName, validDescription, validLocation, invalidEmail, validPhone, validWebContact,
                        validWebsite, AddressException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, invalidPhone1, validWebContact,
                        validWebsite, NumberParseException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, invalidPhone2, validWebContact,
                        validWebsite, NumberParseException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, invalidWebContact,
                        validWebsite, MalformedURLException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, validWebContact, null,
                        null),
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, validWebContact,
                        invalidWebsite, MalformedURLException.class));
    }

    /**
     * Tests that the more strongly-typed constructor can't be called with null arguments.
     *
     * @param aName The institution's name
     * @param aDescription The institution's description
     * @param aLocation The institution's human-readable location
     * @param anEmail The institution's optional email contact
     * @param aPhone The institution's optional phone contact
     * @param aWebContact The institution's optional web contact
     * @param aWebsite The institiution's website
     */
    @ParameterizedTest
    @MethodSource
    void testInstitutionNullArguments(final String aName, final String aDescription, final String aLocation,
            final Optional<InternetAddress> anEmail, final Optional<PhoneNumber> aPhone,
            final Optional<URL> aWebContact, final URL aWebsite) {
        assertThrows(NullPointerException.class, () -> {
            new Institution(aName, aDescription, aLocation, anEmail, aPhone, aWebContact, aWebsite);
        });
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws AddressException
     * @throws MalformedURLException
     * @throws NumberParseException
     */
    static Stream<Arguments> testInstitutionNullArguments()
            throws AddressException, MalformedURLException, NumberParseException {
        final String validName = "Name 3";
        final String validDescription = "Description 3";
        final String validLocation = "Location 3";
        final Optional<InternetAddress> validEmail = Optional.of(new InternetAddress("test2@example.com"));
        final Optional<PhoneNumber> validPhone = Optional.of(PHONE_NUMBER_UTIL.parse("+1 888 200 3000", null));
        final Optional<URL> validWebContact = Optional.of(new URL("http://example.com/3/contact"));
        final URL validWebsite = new URL("http://example.com/3");

        return Stream.of( //
                Arguments.of(null, validDescription, validLocation, validEmail, validPhone, validWebContact,
                        validWebsite), //
                Arguments.of(validName, null, validLocation, validEmail, validPhone, validWebContact, validWebsite), //
                Arguments.of(validName, validDescription, null, validEmail, validPhone, validWebContact,
                        validWebsite), //
                Arguments.of(validName, validDescription, validLocation, null, validPhone, validWebContact,
                        validWebsite), //
                Arguments.of(validName, validDescription, validLocation, validEmail, null, validWebContact,
                        validWebsite), //
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, null, validWebsite), //
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, validWebContact,
                        null));
    }

    /**
     * Tests that passing all empty contact methods throws an error.
     */
    @Test
    void testInstitutionEmptyContactMethods() {
        assertThrows(IllegalArgumentI18nException.class, () -> new Institution("Name 4", "Description 4", "Location 4",
                Optional.empty(), Optional.empty(), Optional.empty(), new URL("http://example.com/4")));
    }

    /**
     * Tests that passing a null {@link JsonObject} throws a {@link NullPointerException}.
     */
    @Test
    void testInstitutionNullJsonObject() {
        assertThrows(NullPointerException.class, () -> new Institution(null));
    }
}
