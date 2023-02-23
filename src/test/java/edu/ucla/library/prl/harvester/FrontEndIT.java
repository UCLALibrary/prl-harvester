
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.jsoup.Jsoup;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

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
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    @SuppressWarnings("rawtypes")
    public void testAdminInterfaceRetrieval(final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint allAssetsResolve = aContext.checkpoint();

        myWebClient.get("/admin").send().onSuccess(response -> {
            aContext.verify(() -> {
                final String responseBody = response.bodyAsString();
                final Stream<Future<Void>> checkLinkElements;
                final Stream<Future<Void>> checkScriptElements;
                final Stream<Future<Void>> checkAllElements;

                assertEquals(HttpStatus.SC_OK, response.statusCode());
                assertTrue(response.getHeader(HttpHeaders.CONTENT_TYPE.toString())
                        .contains(MediaType.TEXT_HTML.toString()));

                // Once the index.html response is verified, make sure all the assets linked within resolve

                checkLinkElements = Jsoup.parse(responseBody).getElementsByTag("link").stream().map(element -> {
                    final String href = element.attr("href");

                    return myWebClient.get(href).send().compose(resp -> checkAssetResponse(href, resp));
                });
                checkScriptElements = Jsoup.parse(responseBody).getElementsByTag("script").stream().map(element -> {
                    final String src = element.attr("src");

                    return myWebClient.get(src).send().compose(resp -> checkAssetResponse(src, resp));
                });
                checkAllElements = Stream.concat(checkLinkElements, checkScriptElements);

                CompositeFuture.all(checkAllElements.map(fut -> (Future) fut).toList()).onSuccess(result -> {
                    allAssetsResolve.flag();
                }).onFailure(aContext::failNow);
            });
        }).onFailure(aContext::failNow);
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
