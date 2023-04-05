
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.apache.http.HttpStatus;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests the front-end application.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class FrontEndIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrontEndIT.class, MessageCodes.BUNDLE);

    private WebClient myWebClient;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final String host = config.getString(Config.HTTP_HOST);
            final int port = config.getInteger(Config.HTTP_PORT);

            myWebClient = WebClient.create(aVertx, new WebClientOptions().setDefaultHost(host).setDefaultPort(port));

            return Future.succeededFuture();
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Tests that the admin interface is retrieved as expected.
     *
     * @param aPath The path to send a GET request to
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @MethodSource
    @SuppressWarnings("rawtypes")
    public void testAdminInterfaceRetrieval(final String aPath, final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint indexHtmlResolves = aContext.checkpoint();
        final Checkpoint linkedAssetsResolve = aContext.checkpoint();

        myWebClient.get(aPath).expect(ResponsePredicate.SC_OK).send().onSuccess(response -> {
            final Document html = Jsoup.parse(response.bodyAsString());

            final Stream<Future<Void>> checkLinkElements = html.getElementsByTag("link").stream().map(elt -> {
                final String href = elt.attr("href");

                return myWebClient.get(href).send().compose(resp -> checkAssetResponse(href, resp));
            });
            final Stream<Future<Void>> checkScriptElements = html.getElementsByTag("script").stream().map(elt -> {
                final String src = elt.attr("src");

                return myWebClient.get(src).send().compose(resp -> checkAssetResponse(src, resp));
            });
            final Stream<Future<Void>> checkAllElements = Stream.concat(checkLinkElements, checkScriptElements);

            // Verify index.html
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_OK, response.statusCode());
                assertTrue(response.getHeader(HttpHeaders.CONTENT_TYPE.toString())
                        .contains(MediaType.TEXT_HTML.toString()));
                assertTrue(html.getElementsByTag("title").first().text().equals("PRL Harvester Admin"));

                indexHtmlResolves.flag();
            });

            // Verify linked asset resolution
            CompositeFuture.all(checkAllElements.map(fut -> (Future) fut).toList()).onSuccess(result -> {
                linkedAssetsResolve.flag();
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     */
    static Stream<Arguments> testAdminInterfaceRetrieval() {
        // The list of paths that should resolve to the admin interface, including via redirect
        return Stream.of(Arguments.of("/admin/"), Arguments.of("/admin"), Arguments.of("/"));
    }

    /**
     * @param anAssetURL The URL of a static asset
     * @param anAssetResponse The HTTP response of a GET request for that asset
     * @return A Future that succeeds if the HTTP status code is 200, or fails otherwise
     */
    private static Future<Void> checkAssetResponse(final String anAssetURL,
            final HttpResponse<Buffer> anAssetResponse) {
        final int statusCode = anAssetResponse.statusCode();

        if (HttpStatus.SC_OK == statusCode) {
            return Future.succeededFuture();
        } else {
            return Future.failedFuture(
                    LOGGER.getMessage(MessageCodes.PRL_036, anAssetURL, statusCode, anAssetResponse.bodyAsString()));
        }
    }
}
