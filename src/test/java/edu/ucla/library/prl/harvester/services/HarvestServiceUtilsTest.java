
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests {@link HarvestServiceUtils}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class HarvestServiceUtilsTest {

    private WebClient myWebClient;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient = WebClient.create(aVertx);

        aContext.completeNow();
    }

    /**
     * Tests {@link HarvestServiceUtils#scoreURL(URL, String, URL)}.
     *
     * @param aURL The URL to score
     * @param aRecordIdentifier The identifier of the record in which the URL was found
     * @param aRepositoryURL The base URL from which the record was harvested
     * @param anExpectedResult
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @CsvSource({ //
        "http://example.edu/catalog/1, oai:0:1, http://example.edu/provider, 2", //
        "http://test.example.edu/catalog/1, oai:0:1, http://example.edu/provider, 1", //
        "http://test.example.edu/catalog/one, oai:0:1, http://example.edu/provider, 0" //
    })
    public void testScoreUrl(final URL aURL, final String aRecordIdentifier, final URL aRepositoryURL,
            final int anExpectedResult, final Vertx aVertx, final VertxTestContext aContext) {
        aContext.verify(() -> {
            assertEquals(anExpectedResult, HarvestServiceUtils.scoreURL(aURL, aRecordIdentifier, aRepositoryURL));
        }).completeNow();
    }

    /**
     * Tests {@link HarvestServiceUtils#isOaiIdentifier(String)}.
     *
     * @param aRecordIdentifier A record identifier
     * @param anExpectedResult
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @CsvSource({ //
        "oai:namespace-identifier:local-identifier, true", //
        "oai:0:1, true", //
        "test, false", //
        "oai:namespace-identifier:, false", //
        "oai:namespace-identifier, false", //
        "oai:0, false", //
        "oai::local-identifier, false", //
        "oai::1, false" //
    })
    public void testIsOaiIdentifier(final String aRecordIdentifier, final boolean anExpectedResult, final Vertx aVertx,
            final VertxTestContext aContext) {
        aContext.verify(() -> {
            assertEquals(anExpectedResult, HarvestServiceUtils.isOaiIdentifier(aRecordIdentifier));
        }).completeNow();
    }

    /**
     * Tests {@link HarvestServiceUtils#findImageURL(List, WebClient)}.
     *
     * @param aPossibleImageUrls The list of URLs to try
     * @param anExpectedResult
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @MethodSource
    public void testFindImageURL(final List<URL> aPossibleImageUrls, final Optional<URL> anExpectedResult,
            final Vertx aVertx, final VertxTestContext aContext) {
        HarvestServiceUtils.findImageURL(aPossibleImageUrls, myWebClient).onSuccess(url -> {
            aContext.verify(() -> {
                assertEquals(anExpectedResult, url);
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws MalformedURLException
     */
    Stream<Arguments> testFindImageURL() throws MalformedURLException {
        final URL imageURL = new URL("https://http.cat/200.jpg");

        return Stream.of( //
                Arguments.of(List.of(new URL("http://example.com"), imageURL), Optional.of(imageURL)), //
                Arguments.of(List.of(new URL("http://example.edu")), Optional.empty()));
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.close();
        aContext.completeNow();
    }
}
