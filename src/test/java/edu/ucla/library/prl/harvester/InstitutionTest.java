
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

/**
 * Tests {@link Institution}.
 */
@ExtendWith(VertxExtension.class)
public class InstitutionTest {

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
     * @param anEmail The optional email address contact of the institution
     * @param aPhone The optional phone number contact of the institution
     * @param aWebContact The optional web contact of the institution
     * @param aWebsite The institiution's website
     */
    @ParameterizedTest
    @MethodSource
    void testInstitutionSerDe(final String aName, final String aDescription, final String aLocation,
            final InternetAddress anEmail, final PhoneNumber aPhone, final URL aWebContact, final URL aWebsite) {
        final Institution institution =
                new Institution(aName, aDescription, aLocation, anEmail, aPhone, aWebContact, aWebsite);
        final JsonObject json = new JsonObject() //
                .put(Institution.NAME, aName) //
                .put(Institution.DESCRIPTION, aDescription) //
                .put(Institution.LOCATION, aLocation) //
                .put(Institution.EMAIL, Optional.ofNullable(anEmail).map(InternetAddress::toString).orElse(null)) //
                .put(Institution.PHONE, Optional.ofNullable(aPhone) //
                        .map(phone -> PHONE_NUMBER_UTIL.format(phone, PhoneNumberFormat.INTERNATIONAL)) //
                        .orElse(null)) //
                .put(Institution.WEB_CONTACT, Optional.ofNullable(aWebContact).map(URL::toString).orElse(null)) //
                .put(Institution.WEBSITE, aWebsite.toString());
        final Institution institutionFromJson = new Institution(json);

        assertEquals(json, institution.toJson());
        assertEquals(institution.toJson(), institutionFromJson.toJson());

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
        final InternetAddress exampleEmail = new InternetAddress("test0@example.com");
        final PhoneNumber examplePhone = PHONE_NUMBER_UTIL.parse("+1 888 200 1000", null);
        final URL exampleWebContact = new URL("http://example.com/1/contact");
        final URL exampleWebsite = new URL("http://example.com/1");

        return Stream.of( //
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, examplePhone,
                        exampleWebContact, exampleWebsite), //
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, examplePhone, null,
                        exampleWebsite), //
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, null, exampleWebContact,
                        exampleWebsite), //
                Arguments.of(exampleName, exampleDescription, exampleLocation, null, examplePhone, exampleWebContact,
                        exampleWebsite), //
                Arguments.of(exampleName, exampleDescription, exampleLocation, exampleEmail, null, null,
                        exampleWebsite), //
                Arguments.of(exampleName, exampleDescription, exampleLocation, null, examplePhone, null,
                        exampleWebsite), //
                Arguments.of(exampleName, exampleDescription, exampleLocation, null, null, exampleWebContact,
                        exampleWebsite));
    }

    /**
     * Tests that an {@link Institution} cannot be instantiated from an invalid JSON representation.
     *
     * @param aName The institution's name
     * @param aDescription The institution's description
     * @param aLocation The institution's human-readable location
     * @param anEmail The optional email address contact of the institution
     * @param aPhone The optional phone number contact of the institution
     * @param aWebContact The optional web contact of the institution
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
        final Exception error = assertThrows(IllegalArgumentException.class, () -> new Institution(json));

        assertEquals(anErrorClass, error.getCause().getClass());
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
                Arguments.of(validName, validDescription, validLocation, invalidEmail, validPhone, validWebContact,
                        validWebsite, AddressException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, invalidPhone1, validWebContact,
                        validWebsite, NumberParseException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, invalidPhone2, validWebContact,
                        validWebsite, NumberParseException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, invalidWebContact,
                        validWebsite, MalformedURLException.class), //
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, validWebContact,
                        invalidWebsite, MalformedURLException.class));
    }

    /**
     * Tests that the more strongly-typed constructor can't be called with certain combinations of null arguments.
     *
     * @param aName The institution's name
     * @param aDescription The institution's description
     * @param aLocation The institution's human-readable location
     * @param anEmail The optional email address contact of the institution
     * @param aPhone The optional phone number contact of the institution
     * @param aWebContact The optional web contact of the institution
     * @param aWebsite The institiution's website
     */
    @ParameterizedTest
    @MethodSource
    void testInstitutionNullArguments(final String aName, final String aDescription, final String aLocation,
            final InternetAddress anEmail, final PhoneNumber aPhone, final URL aWebContact, final URL aWebsite) {
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
        final InternetAddress validEmail = new InternetAddress("test2@example.com");
        final PhoneNumber validPhone = PHONE_NUMBER_UTIL.parse("+1 888 200 3000", null);
        final URL validWebContact = new URL("http://example.com/3/contact");
        final URL validWebsite = new URL("http://example.com/3");

        return Stream.of( //
                Arguments.of(null, validDescription, validLocation, validEmail, validPhone, validWebContact,
                        validWebsite), //
                Arguments.of(validName, null, validLocation, validEmail, validPhone, validWebContact, validWebsite), //
                Arguments.of(validName, validDescription, null, validEmail, validPhone, validWebContact, //
                        validWebsite), //
                Arguments.of(validName, validDescription, validLocation, null, null, null, validWebsite), //
                Arguments.of(validName, validDescription, validLocation, validEmail, validPhone, validWebContact,
                        null));
    }

    /**
     * Tests that passing a null {@link JsonObject} throws a {@link NullPointerException}.
     */
    @Test
    void testInstitutionNullJsonObject() {
        assertThrows(NullPointerException.class, () -> new Institution(null));
    }
}