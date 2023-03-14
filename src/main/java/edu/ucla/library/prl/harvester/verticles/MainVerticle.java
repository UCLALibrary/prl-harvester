
package edu.ucla.library.prl.harvester.verticles;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import java.util.List;
import java.util.Set;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.Op;
import edu.ucla.library.prl.harvester.handlers.AddInstitutionHandler;
import edu.ucla.library.prl.harvester.handlers.AddJobHandler;
import edu.ucla.library.prl.harvester.handlers.GetInstitutionHandler;
import edu.ucla.library.prl.harvester.handlers.GetJobHandler;
import edu.ucla.library.prl.harvester.handlers.ListInstitutionsHandler;
import edu.ucla.library.prl.harvester.handlers.ListJobsHandler;
import edu.ucla.library.prl.harvester.handlers.RemoveInstitutionHandler;
import edu.ucla.library.prl.harvester.handlers.RemoveJobHandler;
import edu.ucla.library.prl.harvester.handlers.ServiceExceptionHandler;
import edu.ucla.library.prl.harvester.handlers.StatusHandler;
import edu.ucla.library.prl.harvester.handlers.UpdateInstitutionHandler;
import edu.ucla.library.prl.harvester.handlers.UpdateJobHandler;
import edu.ucla.library.prl.harvester.services.HarvestJobSchedulerService;
import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;
import edu.ucla.library.prl.harvester.services.HarvestService;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.Pool;

/**
 * Main verticle that starts the application.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class MainVerticle extends AbstractVerticle {

    /**
     * A logger for the main verticle.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class, MessageCodes.BUNDLE);

    /**
     * The main verticle's HTTP server.
     */
    private HttpServer myServer;

    /**
     * A database client.
     */
    private Pool myDbConnectionPool;

    /**
     * The collection of deployed event bus services.
     */
    private Set<MessageConsumer<JsonObject>> myEventBusServices;

    @Override
    public void start(final Promise<Void> aPromise) {
        ConfigRetriever.create(vertx).getConfig().compose(config -> {
            myDbConnectionPool = HarvestScheduleStoreService.getConnectionPool(vertx, config);

            return createEventBusServices(config).compose(services -> {
                myEventBusServices = services;

                return createRouter(config).compose(router -> createHttpServer(config, router));
            });
        }).onSuccess(server -> {
            // Save a reference to the HTTP server so we can close it later
            myServer = server;

            LOGGER.info(MessageCodes.PRL_001, server.actualPort());
            aPromise.complete();
        }).onFailure(aPromise::fail);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void stop(final Promise<Void> aPromise) {
        myServer.close().compose(nil -> {
            final List<Future> closeEventBusServices =
                    myEventBusServices.stream().map(service -> (Future) service.unregister()).toList();

            return CompositeFuture.all(closeEventBusServices).compose(result -> myDbConnectionPool.close());
        }).onFailure(aPromise::fail).onSuccess(result -> aPromise.complete());
    }

    /**
     * Creates the event bus services.
     *
     * @param aConfig A configuration
     * @return A Future that resolves to the event bus services
     */
    public Future<Set<MessageConsumer<JsonObject>>> createEventBusServices(final JsonObject aConfig) {
        final ServiceBinder serviceBinder = new ServiceBinder(vertx);
        final MessageConsumer<JsonObject> harvestService = serviceBinder.setAddress(HarvestService.ADDRESS)
                .register(HarvestService.class, HarvestService.create(vertx, aConfig));
        final MessageConsumer<JsonObject> scheduleStoreService = serviceBinder
                .setAddress(HarvestScheduleStoreService.ADDRESS).register(HarvestScheduleStoreService.class,
                        HarvestScheduleStoreService.create(vertx, myDbConnectionPool));

        return HarvestJobSchedulerService.create(vertx, aConfig).map(service -> {
            final MessageConsumer<JsonObject> schedulerService = serviceBinder
                    .setAddress(HarvestJobSchedulerService.ADDRESS).register(HarvestJobSchedulerService.class, service);

            return Set.of(harvestService, schedulerService, scheduleStoreService);
        });
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
            final ServiceExceptionHandler serviceExceptionHandler = new ServiceExceptionHandler();
            final Router router;

            // Associate handlers with operation IDs from the OpenAPI spec
            routeBuilder.operation(Op.getStatus.name()).handler(new StatusHandler());

            // Institution operations
            routeBuilder.operation(Op.addInstitution.name()).handler(new AddInstitutionHandler(vertx, aConfig));
            routeBuilder.operation(Op.getInstitution.name()).handler(new GetInstitutionHandler(vertx));
            routeBuilder.operation(Op.listInstitutions.name()).handler(new ListInstitutionsHandler(vertx));
            routeBuilder.operation(Op.removeInstitution.name()).handler(new RemoveInstitutionHandler(vertx, aConfig));
            routeBuilder.operation(Op.updateInstitution.name()).handler(new UpdateInstitutionHandler(vertx, aConfig));

            // Job operations
            routeBuilder.operation(Op.addJob.name()).handler(new AddJobHandler(vertx));
            routeBuilder.operation(Op.getJob.name()).handler(new GetJobHandler(vertx));
            routeBuilder.operation(Op.listJobs.name()).handler(new ListJobsHandler(vertx));
            routeBuilder.operation(Op.removeJob.name()).handler(new RemoveJobHandler(vertx, aConfig));
            routeBuilder.operation(Op.updateJob.name()).handler(new UpdateJobHandler(vertx, aConfig));

            // Admin interface
            routeBuilder.operation(Op.getAdmin.name()).handler(StaticHandler.create());

            router = routeBuilder.createRouter();
            router.route("/assets/*").handler(StaticHandler.create("webroot/assets"));
            router.route().handler(FaviconHandler.create(vertx, "webroot/favicon.ico"))
                    .failureHandler(serviceExceptionHandler);

            return router;
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

}
