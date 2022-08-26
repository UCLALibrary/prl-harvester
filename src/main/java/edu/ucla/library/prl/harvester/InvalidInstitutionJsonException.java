
package edu.ucla.library.prl.harvester;

import info.freelibrary.util.I18nRuntimeException;

/**
 * Represents an error in the JSON representation of an {@link Institution}.
 */
public class InvalidInstitutionJsonException extends I18nRuntimeException {

    /**
     * The <code>serialVersionUID</code> for this class.
     */
    private static final long serialVersionUID = 1269493839347809483L;

    /**
     * Instantiates an exception.
     *
     * @param aMessageKey The message key
     */
    public InvalidInstitutionJsonException(final String aMessageKey) {
        super(MessageCodes.BUNDLE, aMessageKey);
    }

    /**
     * Instantiates an exception.
     *
     * @param aMessageKey The message key
     * @param aVarArgs The message details
     */
    public InvalidInstitutionJsonException(final String aMessageKey, final Object... aVarArgs) {
        super(MessageCodes.BUNDLE, aMessageKey, aVarArgs);
    }

    /**
     * Instantiates an exception.
     *
     * @param aCause The cause
     * @param aMessageKey The message key
     * @param aVarArgs The message details
     */
    public InvalidInstitutionJsonException(final Throwable aCause, final String aMessageKey, final Object... aVarArgs) {
        super(aCause, MessageCodes.BUNDLE, aMessageKey, aVarArgs);
    }
}
