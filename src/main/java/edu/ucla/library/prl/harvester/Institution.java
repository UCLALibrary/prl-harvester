
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
     * The institution's contact methods.
     */
    private final ContactMethods myContactMethods;

    /**
     * The institiution's website.
     */
    private final URL myWebsite;

    /**
     * Instantiates an institution.
     *
     * @param aName The institution's name
     * @param aDescription The institution's description
     * @param aLocation The institution's human-readable location
     * @param aContactMethods The institution's contact methods
     * @param aWebsite The institiution's website
     */
    public Institution(final String aName, final String aDescription, final String aLocation,
            final ContactMethods aContactMethods, final URL aWebsite) {
        myName = Objects.requireNonNull(aName);
        myDescription = Objects.requireNonNull(aDescription);
        myLocation = Objects.requireNonNull(aLocation);
        myContactMethods = Objects.requireNonNull(aContactMethods);
        myWebsite = Objects.requireNonNull(aWebsite);
    }

    /**
     * Instantiates an institution from its JSON representation.
     * <p>
     * <b>This constructor is meant to be used only by generated service proxy code!</b>
     * {@link #Institution(String, String, String, ContactMethods, URL)} should be used everywhere else.
     *
     * @param aJsonObject An institution represented as JSON
     * @throws IllegalArgumentException If the JSON representation is invalid
     */
    @SuppressWarnings({ "PMD.AvoidCatchingNPE", "PMD.AvoidCatchingGenericException" })
    public Institution(final JsonObject aJsonObject) {
        Objects.requireNonNull(aJsonObject);
        try {
            final Optional<InternetAddress> email;
            final Optional<PhoneNumber> phone;
            final Optional<URL> webContact;

            myName = Objects.requireNonNull(aJsonObject.getString(NAME));
            myDescription = Objects.requireNonNull(aJsonObject.getString(DESCRIPTION));
            myLocation = Objects.requireNonNull(aJsonObject.getString(LOCATION));

            email = Optional.ofNullable(aJsonObject.getString(EMAIL)).map(rawEmail -> {
                try {
                    return new InternetAddress(rawEmail, true);
                } catch (final AddressException details) {
                    throw new IllegalArgumentException(details.getMessage(), details);
                }
            });
            phone = Optional.ofNullable(aJsonObject.getString(PHONE)).map(rawPhone -> {
                try {
                    return PHONE_NUMBER_UTIL.parse(rawPhone, null);
                } catch (NumberParseException details) {
                    throw new IllegalArgumentException(details.getMessage(), details);
                }
            });
            webContact = Optional.ofNullable(aJsonObject.getString(WEB_CONTACT)).map(rawWebContact -> {
                try {
                    return new URL(rawWebContact);
                } catch (MalformedURLException details) {
                    throw new IllegalArgumentException(details.getMessage(), details);
                }
            });

            if (email.isPresent() && phone.isPresent() && webContact.isPresent()) {
                myContactMethods = new ContactMethods(email.get(), phone.get(), webContact.get());
            } else if (email.isPresent() && phone.isPresent()) {
                myContactMethods = new ContactMethods(email.get(), phone.get());
            } else if (email.isPresent() && webContact.isPresent()) {
                myContactMethods = new ContactMethods(email.get(), webContact.get());
            } else if (phone.isPresent() && webContact.isPresent()) {
                myContactMethods = new ContactMethods(phone.get(), webContact.get());
            } else if (email.isPresent()) {
                myContactMethods = new ContactMethods(email.get());
            } else if (phone.isPresent()) {
                myContactMethods = new ContactMethods(phone.get());
            } else if (webContact.isPresent()) {
                myContactMethods = new ContactMethods(webContact.get());
            } else {
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
                .put(EMAIL, getContactMethods().getEmail().map(InternetAddress::toString).orElse(null)) //
                .put(PHONE, getContactMethods().getPhone() //
                        .map(phone -> PHONE_NUMBER_UTIL.format(phone, PhoneNumberFormat.INTERNATIONAL)) //
                        .orElse(null)) //
                .put(WEB_CONTACT, getContactMethods().getWebContact().map(URL::toString).orElse(null)) //
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
     * @return The contact methods
     */
    public ContactMethods getContactMethods() {
        return myContactMethods;
    }

    /**
     * @return The website
     */
    public URL getWebsite() {
        return myWebsite;
    }

    /**
     * Represents a valid set of contact methods.
     */
    public static final class ContactMethods {

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
         * Instantiates a set of contact methods.
         *
         * @param anEmail An email address
         * @param aPhone A phone number
         * @param aWebContact A web contact URL
         */
        public ContactMethods(final InternetAddress anEmail, final PhoneNumber aPhone, final URL aWebContact) {
            Objects.requireNonNull(anEmail);
            Objects.requireNonNull(aPhone);
            Objects.requireNonNull(aWebContact);

            myEmail = Optional.of(anEmail);
            myPhone = Optional.of(aPhone);
            myWebContact = Optional.of(aWebContact);
        }

        /**
         * Instantiates a set of contact methods.
         *
         * @param anEmail An email address
         * @param aPhone A phone number
         */
        public ContactMethods(final InternetAddress anEmail, final PhoneNumber aPhone) {
            Objects.requireNonNull(anEmail);
            Objects.requireNonNull(aPhone);

            myEmail = Optional.of(anEmail);
            myPhone = Optional.of(aPhone);
            myWebContact = Optional.empty();
        }

        /**
         * Instantiates a set of contact methods.
         *
         * @param anEmail An email address
         * @param aWebContact A web contact URL
         */
        public ContactMethods(final InternetAddress anEmail, final URL aWebContact) {
            Objects.requireNonNull(anEmail);
            Objects.requireNonNull(aWebContact);

            myEmail = Optional.of(anEmail);
            myPhone = Optional.empty();
            myWebContact = Optional.of(aWebContact);
        }

        /**
         * Instantiates a set of contact methods.
         *
         * @param aPhone A phone number
         * @param aWebContact A web contact URL
         */
        public ContactMethods(final PhoneNumber aPhone, final URL aWebContact) {
            Objects.requireNonNull(aPhone);
            Objects.requireNonNull(aWebContact);

            myEmail = Optional.empty();
            myPhone = Optional.of(aPhone);
            myWebContact = Optional.of(aWebContact);
        }

        /**
         * Instantiates a set of contact methods.
         *
         * @param anEmail An email address
         */
        public ContactMethods(final InternetAddress anEmail) {
            Objects.requireNonNull(anEmail);

            myEmail = Optional.of(anEmail);
            myPhone = Optional.empty();
            myWebContact = Optional.empty();
        }

        /**
         * Instantiates a set of contact methods.
         *
         * @param aPhone A phone number
         */
        public ContactMethods(final PhoneNumber aPhone) {
            Objects.requireNonNull(aPhone);

            myEmail = Optional.empty();
            myPhone = Optional.of(aPhone);
            myWebContact = Optional.empty();
        }

        /**
         * Instantiates a set of contact methods.
         *
         * @param aWebContact A web contact URL
         */
        public ContactMethods(final URL aWebContact) {
            Objects.requireNonNull(aWebContact);

            myEmail = Optional.empty();
            myPhone = Optional.empty();
            myWebContact = Optional.of(aWebContact);
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
    }
}
