
package edu.ucla.library.prl.harvester;

/**
 * Application errors that we name and track.
 */
public enum Error {

    /**
     * The failure code if the application is misconfigured (e.g., causing the encryption functionality of the access
     * cookie service to not work properly).
     */
    CONFIGURATION,

    /**
     * The failure code if the access cookie service receives a decryption request for an invalid cookie (e.g., one that
     * is expired, or has been tampered with or stolen).
     */
    INVALID_COOKIE,

    /**
     * The failure code for internal errors with details that shouldn't be reported to clients (e.g., if the database
     * service is unable to query the underlying database).
     */
    INTERNAL_ERROR,

    /**
     * The failure code if the database service runs a "get/update" query that finds no matching rows
     */
    NOT_FOUND,

    /**
     * The failure code if the database service is asked to perform a "set" query with malformed data.
     */
    MALFORMED_INPUT_DATA,

    /**
     * The failure code if data that is expected to be shaped like a JSON array, isn't.
     */
    INVALID_JSONARRAY,

    /**
     * The failure code if no admin credentials were provided with an API request that requires them.
     */
    INVALID_ADMIN_CREDENTIALS;
}
