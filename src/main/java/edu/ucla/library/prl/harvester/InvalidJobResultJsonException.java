
package edu.ucla.library.prl.harvester;

import info.freelibrary.util.I18nRuntimeException;

/**
 * Represents an error in the JSON representation of a {@link JobResult}.
 */
public class InvalidJobResultJsonException extends I18nRuntimeException {

    /**
     * The <code>serialVersionUID</code> for this class.
     */
    private static final long serialVersionUID = 2121139592421503754L;

    /**
     * Instantiates an exception.
     *
     * @param aMessageKey The message key
     */
    public InvalidJobResultJsonException(final String aMessageKey) {
        super(MessageCodes.BUNDLE, aMessageKey);
    }

    /**
     * Instantiates an exception.
     *
     * @param aMessageKey The message key
     * @param aVarArgs The message details
     */
    public InvalidJobResultJsonException(final String aMessageKey, final Object... aVarArgs) {
        super(MessageCodes.BUNDLE, aMessageKey, aVarArgs);
    }

    /**
     * Instantiates an exception.
     *
     * @param aCause The cause
     * @param aMessageKey The message key
     * @param aVarArgs The message details
     */
    public InvalidJobResultJsonException(final Throwable aCause, final String aMessageKey, final Object... aVarArgs) {
        super(aCause, MessageCodes.BUNDLE, aMessageKey, aVarArgs);
    }
}
