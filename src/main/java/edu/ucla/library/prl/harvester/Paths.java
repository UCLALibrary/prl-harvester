
package edu.ucla.library.prl.harvester;

/**
 * Application paths not defined/used by the OpenAPI specification.
 */
public final class Paths {

    /** The path to login to the application's Web interface. */
    public static final String LOGIN = "/login";

    /** The path to log out of the application's Web interface. */
    public static final String LOGOUT = "/logout";

    /** The path to the application's administrative interface. */
    public static final String ADMIN = "/admin/";

    /** The path to the application's Web assets. */
    public static final String ASSETS = "/assets/*";

    /** A regex for paths that should be checked for authorization. */
    public static final String AUTH_CHECKED = "/(?!status|assets|favicon).*";

    /**
     * A private constructor because this is a constants class.
     */
    private Paths() {
        // This is intentionally left empty.
    }
}
