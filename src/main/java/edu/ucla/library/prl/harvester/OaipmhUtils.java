
package edu.ucla.library.prl.harvester;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.dspace.xoai.model.oaipmh.Record;
import org.dspace.xoai.model.oaipmh.Set;
import org.dspace.xoai.serviceprovider.ServiceProvider;
import org.dspace.xoai.serviceprovider.client.HttpOAIClient;
import org.dspace.xoai.serviceprovider.exceptions.BadArgumentException;
import org.dspace.xoai.serviceprovider.exceptions.NoSetHierarchyException;
import org.dspace.xoai.serviceprovider.model.Context;
import org.dspace.xoai.serviceprovider.model.Context.KnownTransformer;
import org.dspace.xoai.serviceprovider.parameters.ListRecordsParameters;

import com.google.common.collect.ImmutableList;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A utility class for working with OAI-PMH asynchronously.
 */
public final class OaipmhUtils {

    /**
     * Private constructor for utility class to prohibit instantiation.
     */
    private OaipmhUtils() {
    }

    /**
     * @param aSets A list of sets
     * @return A list of the setSpecs of those sets
     */
    public static List<String> getSetSpecs(final List<Set> aSets) {
        return aSets.stream().map(Set::getSpec).toList();
    }

    /**
     * Performs a listSets operation.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @return The list of OAI-PMH sets
     */
    public static Future<List<Set>> listSets(final Vertx aVertx, final URL aBaseURL) {
        return listSetsAsyncXoaiWrapper(aVertx, aBaseURL).map(sets -> (List<Set>) sets);
    }

    /**
     * Performs a listRecords operation.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @param aSets The non-empty list of sets to harvest
     * @param aMetadataPrefix The OAI-PMH metadata prefix
     * @param aFrom The optional timestamp of the last successful run
     * @return The list of OAI-PMH records
     */
    public static Future<List<Record>> listRecords(final Vertx aVertx, final URL aBaseURL, final List<String> aSets,
            final String aMetadataPrefix, final Optional<OffsetDateTime> aFrom) {
        @SuppressWarnings("rawtypes")
        final List<Future> selectiveHarvests = new LinkedList<>();

        for (final String setSpec : aSets) {
            final ListRecordsParameters params =
                    ListRecordsParameters.request().withMetadataPrefix(aMetadataPrefix).withSetSpec(setSpec);

            aFrom.ifPresent(from -> params.withFrom(Date.from(from.toInstant())));

            selectiveHarvests.add(listRecordsAsyncXoaiWrapper(aVertx, aBaseURL, params));
        }

        return CompositeFuture.all(selectiveHarvests).map(result -> {
            final List<ImmutableList<Record>> results = result.<ImmutableList<Record>>list();

            // Flatten the list of lists
            return results.parallelStream().flatMap(List::parallelStream).toList();
        });
    }

    /**
     * Provides an asynchronous API for the synchronous XOAI listSets API.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @return A Future that resolves to a list of OAI-PMH sets
     */
    private static Future<ImmutableList<Set>> listSetsAsyncXoaiWrapper(final Vertx aVertx, final URL aBaseURL) {
        final Promise<ImmutableList<Set>> promise = Promise.promise();

        aVertx.<ImmutableList<Set>>executeBlocking(execution -> {
            try {
                final Iterator<Set> synchronousResult = getNewOaipmhClient(aBaseURL).listSets();

                execution.complete(ImmutableList.copyOf(synchronousResult));
            } catch (final NoSetHierarchyException details) {
                execution.fail(details.getCause());
            }
        }, false, promise);

        return promise.future();
    }

    /**
     * Provides an asynchronous API for the synchronous XOAI listRecords API.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @param aParams The OAI-PMH request parameters
     * @return A Future that resolves to a list of OAI-PMH records
     */
    private static Future<ImmutableList<Record>> listRecordsAsyncXoaiWrapper(final Vertx aVertx, final URL aBaseURL,
            final ListRecordsParameters aParams) {
        final Promise<ImmutableList<Record>> promise = Promise.promise();

        aVertx.<ImmutableList<Record>>executeBlocking(execution -> {
            try {
                final Iterator<Record> synchronousResult = getNewOaipmhClient(aBaseURL).listRecords(aParams);

                execution.complete(ImmutableList.copyOf(synchronousResult));
            } catch (final BadArgumentException details) {
                execution.fail(details.getCause());
            }
        }, false, promise);

        return promise.future();
    }

    /**
     * Gets a new OAI-PMH client.
     * <p>
     * Reusing a {@link ServiceProvider} instance causes IllegalStateException due to mishandling of the underlying
     * input stream, so we must instantiate a new one for every OAI-PMH request.
     * <p>
     * Related: <a href="https://github.com/DSpace/xoai/issues/55">DSpace/xoai/issues/55</a>
     *
     * @param aBaseURL An OAI-PMH base URL
     * @return A new OAI-PMH client instance
     */
    private static ServiceProvider getNewOaipmhClient(final URL aBaseURL) {
        final Context context = new Context().withOAIClient(new HttpOAIClient(aBaseURL.toString()))
                .withMetadataTransformer(Constants.OAI_DC, KnownTransformer.OAI_DC);

        return new ServiceProvider(context);
    }
}
