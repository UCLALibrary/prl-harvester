
package edu.ucla.library.prl.harvester;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Represents a provider institution.
 */
@DataObject
@SuppressWarnings("PMD.DataClass")
public final class Institution {

    /**
     * The JSON key for the name.
     */
    static final String NAME = "name";

    /**
     * The JSON key for the description.
     */
    static final String DESCRIPTION = "description";

    /**
     * The JSON key for the location.
     */
    static final String LOCATION = "location";

    /**
     * The JSON key for the email.
     */
    static final String EMAIL = "email";

    /**
     * The JSON key for the phone.
     */
    static final String PHONE = "phone";

    /**
     * The JSON key for the web contact.
     */
    static final String WEB_CONTACT = "webContact";

    /**
     * The JSON key for the website.
     */
    static final String WEBSITE = "website";

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Institution.class, MessageCodes.BUNDLE);

    /**
     * Parses and formats phone numbers.
     */
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    /**
     * The institution's name.
     */
    private final String myName;

    /**
     * The institution's description.
     */
    private final String myDescription;

    /**
     * The institution's human-readable location.
     */
    private final String myLocation;

    /**
     * The optional email address contact of the institution.
     */
    private final Optional<InternetAddress> myEmail;

    /**
     * The optional phone number contact of the institution.
     */
    private final Optional<PhoneNumber> myPhone;

    /**
     * The optional web contact of the institution.
     */
    private final Optional<URL> myWebContact;

    /**
     * The institiution's website.
     */
    private final URL myWebsite;

    /**
     * Instantiates an institution.
     * <p>
     * At least one of {@code anEmail}, {@code aPhone}, or {@code aWebContact} must be provided.
     *
     * @param aName The institution's name
     * @param aDescription The institution's description
     * @param aLocation The institution's human-readable location
     * @param anEmail The optional email address contact of the institution
     * @param aPhone The optional phone number contact of the institution
     * @param aWebContact The optional web contact of the institution
     * @param aWebsite The institiution's website
     */
    @SuppressWarnings("PMD.AvoidThrowingNullPointerException")
    public Institution(final String aName, final String aDescription, final String aLocation,
            final InternetAddress anEmail, final PhoneNumber aPhone, final URL aWebContact, final URL aWebsite) {
        myName = Objects.requireNonNull(aName);
        myDescription = Objects.requireNonNull(aDescription);
        myLocation = Objects.requireNonNull(aLocation);

        if (anEmail == null && aPhone == null && aWebContact == null) {
            throw new NullPointerException(LOGGER.getMessage(MessageCodes.PRL_002));
        } else {
            myEmail = Optional.ofNullable(anEmail);
            myPhone = Optional.ofNullable(aPhone);
            myWebContact = Optional.ofNullable(aWebContact);
        }

        myWebsite = Objects.requireNonNull(aWebsite);
    }

    /**
     * Instantiates an institution from its JSON representation.
     * <p>
     * <b>This constructor is meant to be used only by generated service proxy code!</b>
     * {@link #Institution(String, String, String, InternetAddress, PhoneNumber, URL, URL)} should be used everywhere
     * else.
     *
     * @param aJsonObject An institution represented as JSON
     * @throws IllegalArgumentException If the JSON representation is invalid
     */
    @SuppressWarnings({ "PMD.AvoidCatchingNPE", "PMD.AvoidCatchingGenericException" })
    public Institution(final JsonObject aJsonObject) {
        try {
            myName = Objects.requireNonNull(aJsonObject.getString(NAME));
            myDescription = Objects.requireNonNull(aJsonObject.getString(DESCRIPTION));
            myLocation = Objects.requireNonNull(aJsonObject.getString(LOCATION));
            myEmail = Optional.ofNullable(aJsonObject.getString(EMAIL)).map(email -> {
                try {
                    return new InternetAddress(email, true);
                } catch (final AddressException details) {
                    throw new IllegalArgumentException(details.getMessage(), details);
                }
            });
            myPhone = Optional.ofNullable(aJsonObject.getString(PHONE)).map(phone -> {
                try {
                    return PHONE_NUMBER_UTIL.parse(phone, null);
                } catch (NumberParseException details) {
                    throw new IllegalArgumentException(details.getMessage(), details);
                }
            });
            myWebContact = Optional.ofNullable(aJsonObject.getString(WEB_CONTACT)).map(webContact -> {
                try {
                    return new URL(webContact);
                } catch (MalformedURLException details) {
                    throw new IllegalArgumentException(details.getMessage(), details);
                }
            });

            if (myEmail.isEmpty() && myPhone.isEmpty() && myWebContact.isEmpty()) {
                throw new IllegalArgumentException(LOGGER.getMessage(MessageCodes.PRL_002));
            }

            myWebsite = new URL(Objects.requireNonNull(aJsonObject.getString(WEBSITE)));
        } catch (final MalformedURLException | NullPointerException details) {
            throw new IllegalArgumentException(details.getMessage(), details);
        }
    }

    /**
     * @return The JSON representation of the institution
     */
    public JsonObject toJson() {
        return new JsonObject() //
                .put(NAME, getName()) //
                .put(DESCRIPTION, getDescription()) //
                .put(LOCATION, getLocation()) //
                .put(EMAIL, getEmail().map(InternetAddress::toString).orElse(null)) //
                .put(PHONE, getPhone() //
                        .map(phone -> PHONE_NUMBER_UTIL.format(phone, PhoneNumberFormat.INTERNATIONAL)) //
                        .orElse(null)) //
                .put(WEB_CONTACT, getWebContact().map(URL::toString).orElse(null)) //
                .put(WEBSITE, myWebsite.toString());
    }

    /**
     * @return The name
     */
    public String getName() {
        return myName;
    }

    /**
     * @return The description
     */
    public String getDescription() {
        return myDescription;
    }

    /**
     * @return The location
     */
    public String getLocation() {
        return myLocation;
    }

    /**
     * @return The optional email
     */
    public Optional<InternetAddress> getEmail() {
        return myEmail;
    }

    /**
     * @return The optional phone
     */
    public Optional<PhoneNumber> getPhone() {
        return myPhone;
    }

    /**
     * @return The optional web contact
     */
    public Optional<URL> getWebContact() {
        return myWebContact;
    }

    /**
     * @return The website
     */
    public URL getWebsite() {
        return myWebsite;
    }
}
