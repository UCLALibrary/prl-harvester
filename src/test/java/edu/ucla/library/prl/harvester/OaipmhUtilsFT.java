
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static edu.ucla.library.prl.harvester.Constants.OAI_DC;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.dspace.xoai.model.oaipmh.MetadataFormat;
import org.dspace.xoai.model.oaipmh.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests {@link OaipmhUtils}.
 * <p>
 * This is really more of a unit test, but it needs to run during the integration-test phase since that's when the test
 * OAI-PMH data provider container is running.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class OaipmhUtilsFT {

    /**
     * A logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OaipmhUtils.class, MessageCodes.BUNDLE);

    private String myHarvesterUserAgent;

    private int myOaipmhClientHttpTimeout;

    private URL myTestDataProviderURL;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        Config.getConfig(aVertx).onSuccess(config -> {
            myHarvesterUserAgent = Config.getHarvesterUserAgent(config);
            myOaipmhClientHttpTimeout = Config.getOaipmhClientHttpTimeout(config);

            try {
                myTestDataProviderURL = new URL(config.getString(TestUtils.TEST_PROVIDER_BASE_URL));
            } catch (final MalformedURLException details) {
                aContext.failNow(details);
                return;
            }

            aContext.completeNow();
        });
    }

    /**
     * Tests {@link OaipmhUtils#listSets}.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testListSets(final Vertx aVertx, final VertxTestContext aContext) {
        OaipmhUtils.listSets(aVertx, myTestDataProviderURL, myOaipmhClientHttpTimeout, myHarvesterUserAgent)
                .onSuccess(sets -> {
                    aContext.verify(() -> {
                        assertEquals(2, sets.size());
                        assertTrue(OaipmhUtils.getSetSpecs(sets)
                                .containsAll(java.util.Set.of(TestUtils.SET1, TestUtils.SET2)));
                    }).completeNow();
                }).onFailure(aContext::failNow);
    }

    /**
     * Tests {@link OaipmhUtils#listRecords}.
     *
     * @param aSets The list of setSpec to harvest
     * @param anExpectedRecordCount The expected number of records
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @MethodSource
    public final void testListRecords(final List<String> aSets, final int anExpectedRecordCount, final Vertx aVertx,
            final VertxTestContext aContext) {
        OaipmhUtils.listRecords(aVertx, myTestDataProviderURL, aSets, OAI_DC, Optional.empty(),
                myOaipmhClientHttpTimeout, myHarvesterUserAgent).onSuccess(records -> {
                    final int recordCount;
                    int runningRecordCount = 0;

                    while (records.hasNext()) {
                        records.next();

                        runningRecordCount += 1;
                    }

                    recordCount = runningRecordCount;

                    aContext.verify(() -> {
                        assertEquals(anExpectedRecordCount, recordCount);
                    }).completeNow();
                });
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     */
    static Stream<Arguments> testListRecords() {
        return Stream.of( //
                Arguments.of(List.of(TestUtils.SET1), 2), //
                Arguments.of(List.of(TestUtils.SET2), 3), //
                Arguments.of(List.of(TestUtils.SET1, TestUtils.SET2), 5));
    }

    /**
     * Tests that {@link OaipmhUtils#validateIdentifiers} succeeds when passed valid data.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testValidateIdentifiers(final Vertx aVertx, final VertxTestContext aContext) {
        final List<String> sets = List.of(TestUtils.SET1, TestUtils.SET2);

        OaipmhUtils.validateIdentifiers(aVertx, myTestDataProviderURL, Constants.OAI_DC, sets,
                myOaipmhClientHttpTimeout, myHarvesterUserAgent).onSuccess(nil -> aContext.completeNow())
                .onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link OaipmhUtils#validateIdentifiers} results in failure when passed an invalid base URL.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testValidateIdentifiersInvalidBaseURL(final Vertx aVertx, final VertxTestContext aContext) {
        final URL invalidOaipmhBaseURL;
        final List<String> sets = List.of(TestUtils.SET1, TestUtils.SET2);

        try {
            invalidOaipmhBaseURL = new URL("http://example.com");
        } catch (final MalformedURLException details) {
            aContext.failNow(details);
            return;
        }

        OaipmhUtils.validateIdentifiers(aVertx, invalidOaipmhBaseURL, Constants.OAI_DC, sets, myOaipmhClientHttpTimeout,
                myHarvesterUserAgent).onFailure(details -> {
                    aContext.verify(() -> {
                        assertEquals(LOGGER.getMessage(MessageCodes.PRL_024, invalidOaipmhBaseURL),
                                details.getMessage());
                    }).completeNow();
                }).onSuccess(nil -> aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_038)));
    }

    /**
     * Tests that {@link OaipmhUtils#validateIdentifiers} results in failure when passed an invalid set spec.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testValidateIdentifiersInvalidSetSpec(final Vertx aVertx, final VertxTestContext aContext) {
        final String undefinedSet = "set3";

        OaipmhUtils.validateIdentifiers(aVertx, myTestDataProviderURL, Constants.OAI_DC, List.of(undefinedSet),
                myOaipmhClientHttpTimeout, myHarvesterUserAgent).onFailure(details -> {
                    aContext.verify(() -> {
                        assertEquals(LOGGER.getMessage(MessageCodes.PRL_025, myTestDataProviderURL,
                                Set.class.getSimpleName(), undefinedSet), details.getMessage());
                    }).completeNow();
                }).onSuccess(nil -> aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_038)));
    }

    /**
     * Tests that {@link OaipmhUtils#validateIdentifiers} results in failure when passed an invalid metadata prefix.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testValidateIdentifiersInvalidMetadataPrefix(final Vertx aVertx,
            final VertxTestContext aContext) {
        final List<String> sets = List.of(TestUtils.SET1, TestUtils.SET2);
        final String undefinedMetadataPrefix = "dc_oai";

        OaipmhUtils.validateIdentifiers(aVertx, myTestDataProviderURL, undefinedMetadataPrefix, sets,
                myOaipmhClientHttpTimeout, myHarvesterUserAgent).onFailure(details -> {
                    aContext.verify(() -> {
                        assertEquals(
                                LOGGER.getMessage(MessageCodes.PRL_025, myTestDataProviderURL,
                                        MetadataFormat.class.getSimpleName(), undefinedMetadataPrefix),
                                details.getMessage());
                    }).completeNow();
                }).onSuccess(nil -> aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_038)));
    }
}
