
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
    public static final String DB_HOST = "PGHOSTADDR";

    /**
     * The ENV property for the database name.
     */
    public static final String DB_NAME = "PGDATABASE";

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
     * The ENV property for the harvest timeout (in milliseconds).
     */
    public static final String HARVEST_TIMEOUT = "HARVEST_TIMEOUT";

    /**
     * The ENV property for the Solr core URL.
     */
    public static final String SOLR_CORE_URL = "SOLR_CORE_URL";

    /**
     * The ENV property for the LDAP URL.
     */
    public static final String LDAP_URL = "LDAP_URL";

    /**
     * The ENV property for the LDAP authentication query.
     */
    public static final String LDAP_AUTH_QUERY = "LDAP_AUTH_QUERY";

    /**
     * The ENV property for the LDAP user query.
     */
    public static final String LDAP_USER_QUERY = "LDAP_USER_QUERY";

    /**
     * The ENV property for the attribute name used to authorize the user.
     */
    public static final String LDAP_ATTRIBUTE_KEY = "LDAP_ATTRIBUTE_KEY";

    /**
     * The ENV property for the attribute value used to authorize the user.
     */
    public static final String LDAP_ATTRIBUTE_VALUE = "LDAP_ATTRIBUTE_VALUE";

    // Below are additional configuration options required by test classes (i.e., not the application).

    /**
     * The test configuration property for the data provider's base URL.
     */
    public static final String TEST_PROVIDER_BASE_URL = "TEST_PROVIDER_BASE_URL";

    /**
     * Constant classes should have private constructors.
     */
    private Config() {
        // This is intentionally left empty.
    }

}
