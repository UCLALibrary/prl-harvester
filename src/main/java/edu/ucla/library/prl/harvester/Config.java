
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
     * The ENV property for the database host.
     */
    public static final String DB_HOST = "DB_HOST";

    /**
     * The ENV property for the database name.
     */
    public static final String DB_NAME = "DB_NAME";

    /**
     * The ENV property for the max size of the database connection pool.
     */
    public static final String DB_CONNECTION_POOL_MAX_SIZE = "DB_CONNECTION_POOL_MAX_SIZE";

    /**
     * The ENV property for the number of database reconnect attempts.
     */
    public static final String DB_RECONNECT_ATTEMPTS = "DB_RECONNECT_ATTEMPTS";

    /**
     * The ENV property for the length of the database reconnect interval (in milliseconds).
     */
    public static final String DB_RECONNECT_INTERVAL = "DB_RECONNECT_INTERVAL";

    /**
     * Constant classes should have private constructors.
     */
    private Config() {
        // This is intentionally left empty.
    }

}
