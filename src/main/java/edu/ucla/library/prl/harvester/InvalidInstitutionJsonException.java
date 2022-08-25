package edu.ucla.library.prl.harvester;

import info.freelibrary.util.StringUtils;

/**
 * Represents an error in the JSON representation of an {@link Institution}.
 */
public class InvalidInstitutionJsonException extends IllegalArgumentException {

    /**
     * The message unique to this exception.
     */
    private static final String MESSAGE = "The supplied JsonObject does not represent a valid Institution";

    /**
     * Instantiates an exception.
     */
    public InvalidInstitutionJsonException() {
        super();
    }

    /**
     * Instantiates an exception.
     *
     * @param aMessage The detail message
     */
    public InvalidInstitutionJsonException(final String aMessage) {
        super(StringUtils.format("{}: {}", MESSAGE, aMessage));
    }

    /**
     * Instantiates an exception.
     *
     * @param aMessage The detail message
     * @param aCause The cause
     */
    public InvalidInstitutionJsonException(final String aMessage, final Throwable aCause) {
        super(StringUtils.format("{}: {}", MESSAGE, aMessage), aCause);
    }

    /**
     * Instantiates an exception.
     *
     * @param aCause The cause
     */
    public InvalidInstitutionJsonException(final Throwable aCause) {
        super(MESSAGE, aCause);
    }
}
