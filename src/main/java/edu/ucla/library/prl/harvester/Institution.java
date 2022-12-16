
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

import info.freelibrary.util.IllegalArgumentI18nException;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Represents a provider institution.
 */
@DataObject
@SuppressWarnings("PMD.DataClass")
public final class Institution {

    /**
     * The JSON key for the ID.
     */
    public static final String ID = "id";

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
     * Parses and formats phone numbers.
     */
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    /**
     * The institution's optional identifier.
     */
    private final Optional<Integer> myID;

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
     * The institution's optional email contact.
     */
    private final Optional<InternetAddress> myEmail;

    /**
     * The institution's optional phone contact.
     */
    private final Optional<PhoneNumber> myPhone;

    /**
     * The institution's optional web contact.
     */
    private final Optional<URL> myWebContact;

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
     * @param anEmail The institution's optional email contact
     * @param aPhone The institution's optional phone contact
     * @param aWebContact The institution's optional web contact
     * @param aWebsite The institiution's website
     * @throws IllegalArgumentI18nException If all provided contact methods are empty
     */
    public Institution(final String aName, final String aDescription, final String aLocation,
            final Optional<InternetAddress> anEmail, final Optional<PhoneNumber> aPhone,
            final Optional<URL> aWebContact, final URL aWebsite) {
        myID = Optional.empty();
        myName = Objects.requireNonNull(aName);
        myDescription = Objects.requireNonNull(aDescription);
        myLocation = Objects.requireNonNull(aLocation);

        if (anEmail.isPresent() || aPhone.isPresent() || aWebContact.isPresent()) {
            myEmail = Objects.requireNonNull(anEmail);
            myPhone = Objects.requireNonNull(aPhone);
            myWebContact = Objects.requireNonNull(aWebContact);
        } else {
            throw new IllegalArgumentI18nException(MessageCodes.BUNDLE, MessageCodes.PRL_003);
        }

        myWebsite = Objects.requireNonNull(aWebsite);
    }

    /**
     * Instantiates an institution from its JSON representation.
     * <p>
     * Note that the JSON representation may contain an ID, which must have been assigned by the database.
     * <p>
     * <b>This constructor is meant to be used only by the service code (generated or otherwise)!</b>
     * {@link #Institution(String, String, String, Optional, Optional, Optional, URL)} should be used everywhere else.
     *
     * @param aJsonObject An institution represented as JSON
     * @throws InvalidInstitutionJsonException If the JSON representation is invalid
     */
    @SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.NPathComplexity" })
    public Institution(final JsonObject aJsonObject) {
        Objects.requireNonNull(aJsonObject);

        final String name = aJsonObject.getString(NAME);
        final String description = aJsonObject.getString(DESCRIPTION);
        final String location = aJsonObject.getString(LOCATION);
        final Optional<InternetAddress> email;
        final Optional<PhoneNumber> phone;
        final Optional<URL> webContact;
        final String website = aJsonObject.getString(WEBSITE);

        myID = Optional.ofNullable(aJsonObject.getInteger(ID));

        if (name != null) {
            myName = name;
        } else {
            throw new InvalidInstitutionJsonException(MessageCodes.PRL_002, NAME);
        }

        if (description != null) {
            myDescription = description;
        } else {
            throw new InvalidInstitutionJsonException(MessageCodes.PRL_002, DESCRIPTION);
        }

        if (location != null) {
            myLocation = location;
        } else {
            throw new InvalidInstitutionJsonException(MessageCodes.PRL_002, LOCATION);
        }

        email = Optional.ofNullable(aJsonObject.getString(EMAIL)).map(rawEmail -> {
            try {
                return new InternetAddress(rawEmail, true);
            } catch (final AddressException details) {
                throw new InvalidInstitutionJsonException(details, MessageCodes.PRL_004, EMAIL, details.getMessage());
            }
        });
        phone = Optional.ofNullable(aJsonObject.getString(PHONE)).map(rawPhone -> {
            try {
                return PHONE_NUMBER_UTIL.parse(rawPhone, null);
            } catch (NumberParseException details) {
                throw new InvalidInstitutionJsonException(details, MessageCodes.PRL_004, PHONE, details.getMessage());
            }
        });
        webContact = Optional.ofNullable(aJsonObject.getString(WEB_CONTACT)).map(rawWebContact -> {
            try {
                return new URL(rawWebContact);
            } catch (MalformedURLException details) {
                throw new InvalidInstitutionJsonException(details, MessageCodes.PRL_004, WEB_CONTACT,
                        details.getMessage());
            }
        });

        if (email.isPresent() || phone.isPresent() || webContact.isPresent()) {
            myEmail = email;
            myPhone = phone;
            myWebContact = webContact;
        } else {
            throw new InvalidInstitutionJsonException(MessageCodes.PRL_003);
        }

        if (website != null) {
            try {
                myWebsite = new URL(website);
            } catch (final MalformedURLException details) {
                throw new InvalidInstitutionJsonException(details, MessageCodes.PRL_004, WEBSITE, details.getMessage());
            }
        } else {
            throw new InvalidInstitutionJsonException(MessageCodes.PRL_002, WEBSITE);
        }
    }

    /**
     * @return The JSON representation of the institution
     */
    public JsonObject toJson() {
        final JsonObject json = new JsonObject() //
                .put(NAME, getName()) //
                .put(DESCRIPTION, getDescription()) //
                .put(LOCATION, getLocation()) //
                .put(EMAIL, getEmail().map(InternetAddress::toString).orElse(null)) //
                .put(PHONE, getPhone() //
                        .map(phone -> PHONE_NUMBER_UTIL.format(phone, PhoneNumberFormat.INTERNATIONAL)) //
                        .orElse(null)) //
                .put(WEB_CONTACT, getWebContact().map(URL::toString).orElse(null)) //
                .put(WEBSITE, myWebsite.toString());

        myID.ifPresent(id -> json.put(ID, id));

        return json;
    }

    /**
     * @return The optional ID
     */
    public Optional<Integer> getID() {
        return myID;
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
