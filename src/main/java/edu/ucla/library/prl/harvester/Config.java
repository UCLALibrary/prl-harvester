
package edu.ucla.library.prl.harvester;

/**
 * Properties that are used to configure the application.
 */
public final class Config {

    /**
     * The configuration property for the application's port.
     */
    public static final String HTTP_PORT = "HTTP_PORT";

    /**
     * The configuration property for the application's host.
     */
    public static final String HTTP_HOST = "HTTP_HOST";

    /**
     * The configuration property for the database username.
     */
    public static final String DB_USERNAME = "PGUSER";

    /**
     * The configuration property for the database password.
     */
    public static final String DB_PASSWORD = "PGPASSWORD";

    /**
     * The configuration property for the database port.
     */
    public static final String DB_PORT = "PGPORT";

    /**
     * Constant classes should have private constructors.
     */
    private Config() {
        // This is intentionally left empty.
    }

}
