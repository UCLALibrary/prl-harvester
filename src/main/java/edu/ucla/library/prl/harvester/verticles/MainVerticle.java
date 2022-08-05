
package edu.ucla.library.prl.harvester.verticles;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.Op;
import edu.ucla.library.prl.harvester.handlers.StatusHandler;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.openapi.RouterBuilder;

/**
 * Main verticle that starts the application.
 */
public class MainVerticle extends AbstractVerticle {

    /**
     * A logger for the main verticle.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class, MessageCodes.BUNDLE);

    /**
     * The main verticle's HTTP server.
     */
    private HttpServer myServer;

    @Override
    public void start(final Promise<Void> aPromise) {
        ConfigRetriever.create(vertx).getConfig().compose(config -> {
            return createRouter(config).compose(router -> createHttpServer(config, router));
        }).onSuccess(server -> {
            // Save a reference to the HTTP server so we can close it later
            myServer = server;

            LOGGER.info(MessageCodes.PRL_001, server.actualPort());
            aPromise.complete();
        }).onFailure(aPromise::fail);
    }

    @Override
    public void stop(final Promise<Void> aPromise) {
        myServer.close().onFailure(aPromise::fail).onSuccess(result -> aPromise.complete());
    }

    /**
     * Creates the HTTP request router.
     *
     * @param aConfig A configuration
     * @return A Future that resolves to the HTTP request router
     */
    public Future<Router> createRouter(final JsonObject aConfig) {
        // Load the OpenAPI specification
        return RouterBuilder.create(vertx, "openapi.yaml").map(routeBuilder -> {
            // Associate handlers with operation IDs from the OpenAPI spec
            routeBuilder.operation(Op.GET_STATUS).handler(new StatusHandler(vertx));

            return routeBuilder.createRouter();
        });
    }

    /**
     * Creates the HTTP server.
     *
     * @param aConfig A configuration
     * @param aRouter An HTTP request router
     * @return A Future that resolves to the HTTP server
     */
    public Future<HttpServer> createHttpServer(final JsonObject aConfig, final Router aRouter) {
        final int port = aConfig.getInteger(Config.HTTP_PORT, 8888);

        return vertx.createHttpServer(new HttpServerOptions().setPort(port)).requestHandler(aRouter).listen();
    }

    /**
     * Starts up the main verticle.
     *
     * @param aArgsArray An array of arguments
     */
    @SuppressWarnings("UncommentedMain")
    public static void main(final String[] aArgsArray) {
        Vertx.vertx().deployVerticle(new MainVerticle());
    }
}
