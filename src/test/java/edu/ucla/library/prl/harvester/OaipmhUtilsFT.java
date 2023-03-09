
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static edu.ucla.library.prl.harvester.Constants.OAI_DC;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
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
public class OaipmhUtilsFT {

    private static final String SET1 = "set1";

    private static final String SET2 = "set2";

    /**
     * Tests {@link OaipmhUtils#listSets}.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public final void testListSets(final Vertx aVertx, final VertxTestContext aContext) {
        getTestDataProviderURL(aVertx).compose(url -> {
            return OaipmhUtils.listSets(aVertx, url);
        }).onSuccess(sets -> {
            aContext.verify(() -> {
                assertEquals(2, sets.size());
                assertTrue(OaipmhUtils.getSetSpecs(sets).containsAll(java.util.Set.of(SET1, SET2)));
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
        getTestDataProviderURL(aVertx).compose(url -> {
            return OaipmhUtils.listRecords(aVertx, url, aSets, OAI_DC, Optional.empty());
        }).onSuccess(records -> {
            aContext.verify(() -> {
                assertEquals(anExpectedRecordCount, records.size());
            }).completeNow();
        });
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     */
    static Stream<Arguments> testListRecords() {
        return Stream.of( //
                Arguments.of(List.of(SET1), 2), //
                Arguments.of(List.of(SET2), 3), //
                Arguments.of(List.of(SET1, SET2), 5));
    }

    /**
     * @param aVertx A Vert.x instance
     * @return The base URL of the test OAI-PMH data provider
     */
    private static Future<URL> getTestDataProviderURL(final Vertx aVertx) {
        return ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            try {
                return Future.succeededFuture(new URL(config.getString(Config.TEST_PROVIDER_BASE_URL)));
            } catch (final MalformedURLException details) {
                return Future.failedFuture(details);
            }
        });
    }
}
