
package edu.ucla.library.prl.harvester.services;

import edu.ucla.library.prl.harvester.Job;

import io.vertx.codegen.annotations.ProxyClose;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceProxyBuilder;

/**
 * The interface of the event bus service that runs harvest jobs.
 */
@ProxyGen
@VertxGen
public interface HarvestService {

    /**
     * The event bus address that the service will be registered on, for access via service proxies.
     */
    String ADDRESS = HarvestService.class.getName();

    /**
     * Creates an instance of the service.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     * @return The service instance
     */
    static HarvestService create(final Vertx aVertx, final JsonObject aConfig) {
        // FIXME: this is incorrect, instantiate an implementing class instead
        // TODO: depending on implementation, consider returning Future<HarvestService> instead
        return createProxy(aVertx);
    }

    /**
     * Creates an instance of the service proxy.
     *
     * @param aVertx A Vert.x instance
     * @return A service proxy instance
     */
    static HarvestService createProxy(final Vertx aVertx) {
        return new ServiceProxyBuilder(aVertx).setAddress(ADDRESS).build(HarvestService.class);
    }

    /**
     * Runs a harvest job.
     *
     * @param aJob The harvest job to run
     * @return A Future that succeeds if the harvest job succeeded
     */
    Future<Object> run(Job aJob); // FIXME: use more appropriate return type

    /**
     * Closes the underlying resources used by this service.
     *
     * @return A Future that succeeds once the resources have been closed
     */
    @ProxyClose
    Future<Void> close();
}
