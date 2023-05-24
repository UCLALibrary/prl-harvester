
package edu.ucla.library.prl.harvester.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static info.freelibrary.util.Constants.INADDR_ANY;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import info.freelibrary.util.HTTP;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.MediaType;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests {@link StatusHandler#handle}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class StatusHandlerIT {

    /**
     * A WebClient for calling the HTTP API.
     */
    private WebClient myWebClient;

    /**
     * The port on which the application is listening.
     */
    private int myPort;

    /**
     * Sets up the test.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        Config.getConfig(aVertx).onSuccess(config -> {
            myWebClient = WebClient.create(aVertx);
            myPort = Config.getHttpPort(config);

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * Tests the status handler.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public void testGetStatus(final Vertx aVertx, final VertxTestContext aContext) {
        final HttpRequest<?> getStatus = myWebClient.get(myPort, INADDR_ANY, "/status");

        getStatus.send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HTTP.OK, response.statusCode());
                assertEquals(MediaType.APPLICATION_JSON.toString(), response.headers().get(HttpHeaders.CONTENT_TYPE));
            }).completeNow();
        }).onFailure(aContext::failNow);
    }
}
