
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.HttpStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.ucla.library.prl.harvester.utils.TestUtils;
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
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests the front-end application.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class FrontEndIT extends AuthorizedFIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrontEndIT.class, MessageCodes.BUNDLE);

    private WebClient myWebClient;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final String host = config.getString(TestUtils.HTTP_HOST);
            final int port = Config.getHttpPort(config);
            final WebClientOptions webClientOpts = new WebClientOptions().setDefaultHost(host).setDefaultPort(port);

            myWebClient = WebClientSession.create(WebClient.create(aVertx, webClientOpts));

            return authorize(myWebClient, config);
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
    @ValueSource(strings = { "/admin/", "/admin", "/" })
    public void testAdminInterfaceRetrieval(final String aPath, final Vertx aVertx, final VertxTestContext aContext) {
        final Checkpoint indexHtmlResolves = aContext.checkpoint();
        final Checkpoint linkedAssetsResolve = aContext.checkpoint();

        myWebClient.get(aPath).expect(ResponsePredicate.SC_OK).send().onSuccess(response -> {
            final Document html = Jsoup.parse(response.bodyAsString());

            final Stream<Future<Void>> checkLinkElements = html.getElementsByTag("link").stream().map(elt -> {
                final String href = elt.attr("href");

                return resolveAsset(myWebClient, href).mapEmpty();
            });
            final Stream<Future<Void>> checkScriptElements = html.getElementsByTag("script").stream().map(elt -> {
                final String src = elt.attr("src");

                return resolveAsset(myWebClient, src).mapEmpty();
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
            CompositeFuture.all(checkAllElements.collect(Collectors.toList())).onSuccess(result -> {
                linkedAssetsResolve.flag();
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * @param aWebClient A web client
     * @param anAssetURL The URL of a static asset
     * @return A Future that succeeds if the HTTP response status code is 200, or fails otherwise
     */
    private static Future<HttpResponse<Buffer>> resolveAsset(final WebClient aWebClient, final String anAssetURL) {
        return aWebClient.get(anAssetURL).expect(ResponsePredicate.SC_OK).send().recover(details -> {
            return Future.failedFuture(LOGGER.getMessage(MessageCodes.PRL_036, anAssetURL, details.getMessage()));
        });
    }
}
