
package edu.ucla.library.prl.harvester;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;
import info.freelibrary.util.StringUtils;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Properties that are used to configure the application, and helper methods for accessing them.
 */
public final class Config {

    /**
     * The configuration property for the application's port.
     */
    public static final String HTTP_PORT = "HTTP_PORT";

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
     * The ENV property for the User-Agent HTTP request header to use for outgoing requests.
     */
    public static final String HARVESTER_USER_AGENT = "HARVESTER_USER_AGENT";

    /**
     * The ENV property for the attribute name used to authorize the user.
     */
    public static final String LDAP_ATTRIBUTE_KEY = "LDAP_ATTRIBUTE_KEY";

    /**
     * The ENV property for the attribute value used to authorize the user.
     */
    public static final String LDAP_ATTRIBUTE_VALUE = "LDAP_ATTRIBUTE_VALUE";

    /**
     * The ENV property for the LDAP authentication query.
     */
    public static final String LDAP_AUTH_QUERY = "LDAP_AUTH_QUERY";

    /**
     * The ENV property for the LDAP user query.
     */
    public static final String LDAP_USER_QUERY = "LDAP_USER_QUERY";

    /**
     * The ENV property for the LDAP URL.
     */
    public static final String LDAP_URL = "LDAP_URL";

    /**
     * The ENV property for the HTTP timeout of the internal OAI-PMH client.
     */
    public static final String OAIPMH_CLIENT_HTTP_TIMEOUT = "OAIPMH_CLIENT_HTTP_TIMEOUT";

    /**
     * The ENV property for the Solr core URL.
     */
    public static final String SOLR_CORE_URL = "SOLR_CORE_URL";

    /**
     * A logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class, MessageCodes.BUNDLE);

    /**
     * The name for the application version.
     */
    private static final String VERSION = "VERSION";

    /**
     * Constant classes should have private constructors.
     */
    private Config() {
        // This is intentionally left empty.
    }

    /**
     * Gets the application configuration.
     *
     * @param aVertx A Vert.x instance
     * @return The configuration
     */
    public static Future<JsonObject> getConfig(final Vertx aVertx) {
        return ConfigRetriever.create(aVertx).setConfigurationProcessor(Config::setAppVersion).getConfig();
    }

    /**
     * A configuration processor that adds the application version, if available.
     *
     * @param aConfig An application configuration
     * @return The processed application configuration
     */
    private static JsonObject setAppVersion(final JsonObject aConfig) {
        final String manifestPath = "/META-INF/MANIFEST.MF";

        try (InputStream manifest = Config.class.getResourceAsStream(manifestPath)) {
            final Properties properties = new Properties();
            final Optional<String> version;

            properties.load(manifest);
            version = Optional.ofNullable(properties.getProperty("Maven-Version"));

            if (version.isPresent()) {
                return aConfig.copy().put(VERSION, version.get());
            } else {
                return aConfig;
            }
        } catch (final IOException details) {
            // Either the app wasn't deployed as a JAR, or Vert.x Maven Plugin isn't creating the manifest file
            LOGGER.warn(MessageCodes.PRL_048, manifestPath, details.getMessage());

            return aConfig;
        }
    }

    /**
     * Gets the User-Agent HTTP request header to use for outgoing requests.
     *
     * @param aConfig A configuration
     * @return The user agent
     */
    public static String getHarvesterUserAgent(final JsonObject aConfig) {
        final String productName =
                aConfig.getString(Config.HARVESTER_USER_AGENT, Constants.DEFAULT_HARVESTER_USER_AGENT);
        final Optional<String> productVersion = Optional.ofNullable(aConfig.getString(VERSION));

        if (productVersion.isPresent()) {
            return StringUtils.format("{}/{}", productName, productVersion.get());
        } else {
            return productName;
        }
    }

    /**
     * Gets the application's port.
     *
     * @param aConfig A configuration
     * @return The port
     */
    public static int getHttpPort(final JsonObject aConfig) {
        return aConfig.getInteger(Config.HTTP_PORT, Constants.DEFAULT_HTTP_PORT);
    }

    /**
     * Gets the HTTP timeout to use with the internal OAI-PMH client.
     *
     * @param aConfig A configuration
     * @return The timeout
     */
    public static int getOaipmhClientHttpTimeout(final JsonObject aConfig) {
        return aConfig.getInteger(Config.OAIPMH_CLIENT_HTTP_TIMEOUT, Constants.DEFAULT_OAIPMH_CLIENT_HTTP_TIMEOUT);
    }
}
