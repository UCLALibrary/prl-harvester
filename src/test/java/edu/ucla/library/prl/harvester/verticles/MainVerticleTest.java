
package edu.ucla.library.prl.harvester.verticles;

import static info.freelibrary.util.Constants.INADDR_ANY;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import edu.ucla.library.prl.harvester.Config;

import info.freelibrary.util.HTTP;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests the main verticle of the Vert.x application.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class MainVerticleTest {

    /**
     * Sets up the test.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        aVertx.deployVerticle(MainVerticle.class.getName()).onSuccess(unused -> aContext.completeNow())
                .onFailure(aContext::failNow);
    }

    /**
     * Tests the server can start successfully.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    public void testThatTheServerIsStarted(final Vertx aVertx, final VertxTestContext aContext) {
        final WebClient client = WebClient.create(aVertx);
        final int port = Integer.parseInt(System.getenv(Config.HTTP_PORT));

        client.get(port, INADDR_ANY, "/status").send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HTTP.OK, response.statusCode());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }
}
