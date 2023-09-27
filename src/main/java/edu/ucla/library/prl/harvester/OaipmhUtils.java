
package edu.ucla.library.prl.harvester;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.iterators.IteratorChain;
import org.dspace.xoai.model.oaipmh.MetadataFormat;
import org.dspace.xoai.model.oaipmh.Record;
import org.dspace.xoai.model.oaipmh.Set;
import org.dspace.xoai.serviceprovider.ServiceProvider;
import org.dspace.xoai.serviceprovider.client.HttpOAIClient;
import org.dspace.xoai.serviceprovider.client.OAIClient;
import org.dspace.xoai.serviceprovider.exceptions.BadArgumentException;
import org.dspace.xoai.serviceprovider.exceptions.HttpException;
import org.dspace.xoai.serviceprovider.exceptions.IdDoesNotExistException;
import org.dspace.xoai.serviceprovider.exceptions.NoSetHierarchyException;
import org.dspace.xoai.serviceprovider.model.Context;
import org.dspace.xoai.serviceprovider.model.Context.KnownTransformer;
import org.dspace.xoai.serviceprovider.parameters.ListRecordsParameters;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A utility class for working with OAI-PMH asynchronously.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public final class OaipmhUtils {

    /**
     * A logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OaipmhUtils.class, MessageCodes.BUNDLE);

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
     * @param aMetadataFormats A list of metadata formats
     * @return A list of the names of those metadata formats
     */
    private static List<String> getMetadataFormats(final List<MetadataFormat> aMetadataFormats) {
        return aMetadataFormats.stream().map(MetadataFormat::getMetadataPrefix).toList();
    }

    /**
     * Checks that the given URL points to an OAI-PMH repository, and (if provided) that the sets are defined.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL A URL to check
     * @param aMetadataPrefix A metadata prefix to check
     * @param aSets A list of sets to check
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return A Future that succeeds if the checks pass, and fails otherwise
     */
    public static Future<Void> validateIdentifiers(final Vertx aVertx, final URL aBaseURL, final String aMetadataPrefix,
            final List<String> aSets, final int aTimeout, final String aUserAgent) {
        final Promise<Void> setValidation = Promise.promise();
        final Promise<Void> metadataPrefixValidation = Promise.promise();

        OaipmhUtils.listSets(aVertx, aBaseURL, aTimeout, aUserAgent).onSuccess(sets -> {
            final List<String> setSpecs = OaipmhUtils.getSetSpecs(sets);

            for (final String set : aSets) {
                if (!setSpecs.contains(set)) {
                    setValidation
                            .fail(LOGGER.getMessage(MessageCodes.PRL_025, aBaseURL, Set.class.getSimpleName(), set));
                }
            }

            setValidation.complete();
        }).onFailure(details -> {
            setValidation.fail(LOGGER.getMessage(MessageCodes.PRL_024, aBaseURL));
        });

        OaipmhUtils.listMetadataFormats(aVertx, aBaseURL, aTimeout, aUserAgent).onSuccess(formats -> {
            final List<String> metadataFormats = OaipmhUtils.getMetadataFormats(formats);

            if (metadataFormats.contains(aMetadataPrefix)) {
                metadataPrefixValidation.complete();
            } else {
                metadataPrefixValidation.fail(LOGGER.getMessage(MessageCodes.PRL_025, aBaseURL,
                        MetadataFormat.class.getSimpleName(), aMetadataPrefix));
            }
        }).onFailure(details -> {
            metadataPrefixValidation.fail(LOGGER.getMessage(MessageCodes.PRL_024, aBaseURL));
        });

        return CompositeFuture.all(List.of(setValidation.future(), metadataPrefixValidation.future())).mapEmpty();
    }

    /**
     * Performs a listMetadataFormats operation.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return The list of OAI-PMH metadata formats
     */
    public static Future<List<MetadataFormat>> listMetadataFormats(final Vertx aVertx, final URL aBaseURL,
            final int aTimeout, final String aUserAgent) {
        return listMetadataFormatsAsyncXoaiWrapper(aVertx, aBaseURL, aTimeout, aUserAgent)
                .map(formats -> (List<MetadataFormat>) formats);
    }

    /**
     * Performs a listSets operation.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return The list of OAI-PMH sets
     */
    public static Future<List<Set>> listSets(final Vertx aVertx, final URL aBaseURL, final int aTimeout,
            final String aUserAgent) {
        return listSetsAsyncXoaiWrapper(aVertx, aBaseURL, aTimeout, aUserAgent).map(sets -> (List<Set>) sets);
    }

    /**
     * Performs a listRecords operation.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @param aSets The non-empty list of sets to harvest
     * @param aMetadataPrefix The OAI-PMH metadata prefix
     * @param aFrom The optional timestamp of the last successful run
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return An iterator on OAI-PMH records
     */
    public static Future<Iterator<Record>> listRecords(final Vertx aVertx, final URL aBaseURL, final List<String> aSets,
            final String aMetadataPrefix, final Optional<OffsetDateTime> aFrom, final int aTimeout,
            final String aUserAgent) {
        final Stream<Future<Iterator<Record>>> listRecordsPerSet = aSets.stream().map(setSpec -> {
            final ListRecordsParameters params =
                    ListRecordsParameters.request().withMetadataPrefix(aMetadataPrefix).withSetSpec(setSpec);

            aFrom.ifPresent(from -> params.withFrom(Date.from(from.toInstant())));

            return listRecordsAsyncXoaiWrapper(aVertx, aBaseURL, params, aTimeout, aUserAgent);
        });

        return CompositeFuture.all(listRecordsPerSet.collect(Collectors.toList())).map(result -> {
            final List<Iterator<Record>> results = result.<Iterator<Record>>list();

            // Flatten the list of iterators
            return new IteratorChain(results);
        });
    }

    /**
     * Provides an asynchronous API for the synchronous XOAI listMetadataFormats API.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return A Future that resolves to a list of OAI-PMH metadata formats
     */
    private static Future<List<MetadataFormat>> listMetadataFormatsAsyncXoaiWrapper(final Vertx aVertx,
            final URL aBaseURL, final int aTimeout, final String aUserAgent) {
        final Promise<List<MetadataFormat>> promise = Promise.promise();

        aVertx.<List<MetadataFormat>>executeBlocking(execution -> {
            try {
                final Iterator<MetadataFormat> synchronousResult =
                        getNewOaipmhClient(aBaseURL, aTimeout, aUserAgent).listMetadataFormats();

                execution.complete(IteratorUtils.toList(synchronousResult));
            } catch (final HttpException | IdDoesNotExistException details) {
                execution.fail(details.getCause());
            }
        }, false, promise);

        return promise.future();
    }

    /**
     * Provides an asynchronous API for the synchronous XOAI listSets API.
     *
     * @param aVertx A Vert.x instance
     * @param aBaseURL The OAI-PMH repository base URL
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return A Future that resolves to a list of OAI-PMH sets
     */
    private static Future<List<Set>> listSetsAsyncXoaiWrapper(final Vertx aVertx, final URL aBaseURL,
            final int aTimeout, final String aUserAgent) {
        final Promise<List<Set>> promise = Promise.promise();

        aVertx.<List<Set>>executeBlocking(execution -> {
            try {
                final Iterator<Set> synchronousResult = getNewOaipmhClient(aBaseURL, aTimeout, aUserAgent).listSets();

                execution.complete(IteratorUtils.toList(synchronousResult));
            } catch (final HttpException | NoSetHierarchyException details) {
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
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return A Future that resolves to an iterator on OAI-PMH records
     */
    private static Future<Iterator<Record>> listRecordsAsyncXoaiWrapper(final Vertx aVertx, final URL aBaseURL,
            final ListRecordsParameters aParams, final int aTimeout, final String aUserAgent) {
        final Promise<Iterator<Record>> promise = Promise.promise();

        aVertx.<Iterator<Record>>executeBlocking(execution -> {
            try {
                execution.complete(getNewOaipmhClient(aBaseURL, aTimeout, aUserAgent).listRecords(aParams));
            } catch (final BadArgumentException | HttpException details) {
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
     * @param aTimeout The value to use for the HTTP timeout
     * @param aUserAgent The value to use for the User-Agent HTTP request header
     * @return A new OAI-PMH client instance
     * @throws HttpException It won't because of how {@link HttpOAIClient#HttpOAIClient(String, List, int, String)} is
     *         being called with an empty list of exceptional URLs
     */
    private static ServiceProvider getNewOaipmhClient(final URL aBaseURL, final int aTimeout, final String aUserAgent)
            throws HttpException {
        final OAIClient client = new HttpOAIClient(aBaseURL.toString(), List.of(), aTimeout, aUserAgent);
        final Context context =
                new Context().withOAIClient(client).withMetadataTransformer(Constants.OAI_DC, KnownTransformer.OAI_DC);

        return new ServiceProvider(context);
    }
}
