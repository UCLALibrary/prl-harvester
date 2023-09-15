
package edu.ucla.library.prl.harvester;

/**
 * Constant values.
 */
public final class Constants {

    /**
     * The metadata prefix for OAI-PMH harvesting (currently we only support Dublin Core).
     */
    public static final String OAI_DC = "oai_dc";

    /**
     * The default value for the User-Agent HTTP request header.
     */
    public static final String DEFAULT_HARVESTER_USER_AGENT = "PRL-Harvester";

    /**
     * The default value for the HTTP port.
     */
    public static final int DEFAULT_HTTP_PORT = 8888;

    /**
     * The default value for the HTTP timeout of the internal OAI-PMH client.
     */
    public static final int DEFAULT_OAIPMH_CLIENT_HTTP_TIMEOUT = 60_000;

    /**
     * The default value for the max batch size for Solr update queries.
     */
    public static final Integer DEFAULT_SOLR_UPDATE_MAX_BATCH_SIZE = 1000;

    /**
     * Constant classes should have private constructors.
     */
    private Constants() {
    }
}
