
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
    public static final String DEFAULT_HARVESTER_USER_AGENT = "PRL Harvester";

    /**
     * The default value for the HTTP port.
     */
    public static final int DEFAULT_HTTP_PORT = 8888;

    /**
     * Constant classes should have private constructors.
     */
    private Constants() {
    }
}
