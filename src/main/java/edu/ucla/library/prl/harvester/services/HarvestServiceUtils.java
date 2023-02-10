
package edu.ucla.library.prl.harvester.services;

import static info.freelibrary.util.Constants.COLON;
import static info.freelibrary.util.Constants.EMPTY;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.solr.common.SolrInputDocument;

import org.dspace.xoai.model.oaipmh.Record;
import org.dspace.xoai.model.xoai.Element;

import edu.ucla.library.prl.harvester.MessageCodes;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

/**
 * A collection of utility methods for processing OAI-PMH records.
 */
@SuppressWarnings("PMD.ExcessiveImports")
final class HarvestServiceUtils {

    /**
     * A logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestServiceUtils.class, MessageCodes.BUNDLE);

    /**
     * dc:date
     */
    private static final String DC_DATE = "date";

    /**
     * dc:description
     */
    private static final String DC_DESCRIPTION = "description";

    /**
     * dc:identifier
     */
    private static final String DC_IDENTIFIER = "identifier";

    /**
     * dc:title
     */
    private static final String DC_TITLE = "title";

    /**
     * The list of Dublin Core elements.
     */
    private static final Set<String> DC_ELEMENTS =
            Set.of(DC_TITLE, "creator", "subject", DC_DESCRIPTION, "publisher", "contributor", DC_DATE, "type",
                    "format", DC_IDENTIFIER, "source", "language", "relation", "coverage", "rights");

    /**
     * The list of Dublin Core elements that we expect may contain a thumbnail URL.
     */
    private static final Set<String> THUMBNAIL_URL_FIELDS =
            Set.of(DC_DESCRIPTION, DC_IDENTIFIER, "identifier.thumbnail");

    /**
     * The pattern for Dublin Core elements that we expect may contain an item URL.
     */
    private static final Pattern ITEM_URL_FIELD_PATTERN = Pattern.compile("identifier(?:\\..+)?");

    /**
     * Private constructor for utility class to prohibit instantiation.
     */
    private HarvestServiceUtils() {
    }

    /**
     * Transforms an OAI-PMH record into a PRL Solr document.
     *
     * @param aRecord A Dublin Core record
     * @param anInstitutionName The name of the associated institution
     * @param aBaseURL An OAI-PMH repository base URL
     * @param aSetNameLookup A lookup table that maps setSpec to setName
     * @param aWebClient An HTTP client for checking thumbnail URLs
     * @return The record transformed to a Solr document
     */
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition", "PMD.CognitiveComplexity", "PMD.EmptyCatchBlock",
        "PMD.ExcessiveMethodLength", "PMD.NPathComplexity" })
    static Future<SolrInputDocument> getSolrDocument(final Record aRecord, final String anInstitutionName,
            final URL aBaseURL, final Map<String, String> aSetNameLookup, final WebClient aWebClient) {
        final SolrInputDocument doc = new SolrInputDocument();
        final Map<String, List<String>> dcElementsMap = new HashMap<>();
        final List<URL> possibleThumbnailUrls = new LinkedList<>();
        final List<String> setNames = new LinkedList<>();

        final String recordIdentifier = aRecord.getHeader().getIdentifier();
        final List<String> setSpecs = aRecord.getHeader().getSetSpecs();

        // Get an iterator over the elements inside the top-level "dc" element
        final List<Element> allElements = aRecord.getMetadata().getValue().getElements().get(0).getElements();

        doc.addField("id", recordIdentifier);
        doc.addField("institutionName", anInstitutionName);

        for (final String setSpec : setSpecs) {
            setNames.add(aSetNameLookup.get(setSpec));
        }

        doc.addField("collectionName", setNames);

        for (final Element element : allElements) {
            final String name = element.getName();
            // Each element contains just a text node
            final String value = element.getFields().get(0).getValue();

            if (THUMBNAIL_URL_FIELDS.contains(name)) {
                try {
                    possibleThumbnailUrls.add(new URL(value));
                } catch (final MalformedURLException details) {
                    // No worries, it's just not a URL
                }
            }
        }

        return findImageURL(possibleThumbnailUrls, aWebClient).map(thumbnailURL -> {
            final List<URL> possibleItemUrls = new LinkedList<>();
            final List<String> stringifiedItemUrls;

            if (thumbnailURL.isPresent()) {
                LOGGER.debug(MessageCodes.PRL_018, recordIdentifier, thumbnailURL.get());

                doc.addField("thumbnail_url", thumbnailURL.get().toString());
            }

            for (final Element element : allElements) {
                final String name = element.getName();
                final String value = element.getFields().get(0).getValue();
                final boolean valueIsNotThumbnailURL =
                        thumbnailURL.map(url -> !url.toString().equals(value)).orElse(true);

                // Skip over the thumbnail URL
                if (ITEM_URL_FIELD_PATTERN.matcher(name).matches() && valueIsNotThumbnailURL) {
                    try {
                        possibleItemUrls.add(new URL(value));
                    } catch (final MalformedURLException details) {
                        // No worries, it's just not a URL
                    }
                }
            }

            possibleItemUrls.sort((url1, url2) -> {
                // The higher-scoring URL should be placed first
                // If url1 scores higher than url2, put url1 first (negative return value); otherwise, url2 first
                // (positive)
                return scoreURL(url2, recordIdentifier, aBaseURL) - scoreURL(url1, recordIdentifier, aBaseURL);
            });

            // SolrInputDocument wants strings
            stringifiedItemUrls = unwrapUrls(possibleItemUrls);

            if (!stringifiedItemUrls.isEmpty()) {
                // The URL with the highest score is probably the canonical item URL
                doc.addField("external_link", stringifiedItemUrls.get(0).toString());
            }
            if (stringifiedItemUrls.size() > 1) {
                // All other URLs go in this field
                doc.addField("alternate_external_link", stringifiedItemUrls.subList(1, stringifiedItemUrls.size()));
            }

            for (final Element element : allElements) {
                final String name = element.getName();
                // Each element contains just a text node
                final String value = element.getFields().get(0).getValue();
                final boolean valueIsNotThumbnailURL =
                        thumbnailURL.map(url -> !url.toString().equals(value)).orElse(true);

                // Skip over the item URLs and thumbnail URL
                if (DC_ELEMENTS.contains(name) && valueIsNotThumbnailURL && !stringifiedItemUrls.contains(value)) {
                    if (dcElementsMap.containsKey(name)) {
                        dcElementsMap.get(name).add(value);
                    } else {
                        // First instance of element
                        final List<String> elements = new LinkedList<>();

                        elements.add(value);
                        dcElementsMap.put(name, elements);
                    }
                }
            }

            // Now that we know which values are item URLs and thumbnail URLs, we can avoid them
            for (final Entry<String, List<String>> entry : dcElementsMap.entrySet()) {
                doc.addField(entry.getKey() + "_keyword", entry.getValue());

                switch (entry.getKey()) {
                    case DC_DATE:
                        final List<Integer> decades = DateUtils.getDecadesAscending(entry.getValue());

                        if (!decades.isEmpty()) {
                            doc.addField("decade", decades);
                            doc.addField("sort_decade", decades.get(0));
                        }
                        break;
                    case DC_TITLE:
                        doc.addField("first_title", entry.getValue().get(0));
                        break;
                    default:
                        break;
                }
            }

            return doc;
        });
    }

    /**
     * @param aUrlList A list of URLs
     * @return A list of the URLs in string form
     */
    private static List<String> unwrapUrls(final List<URL> aUrlList) {
        final List<String> strings = new LinkedList<>();

        for (final URL url : aUrlList) {
            strings.add(url.toString());
        }

        return strings;
    }

    /**
     * Assigns a score to a URL based on how likely it is to be a record's canonical item URL, according to a simple
     * heuristic.
     * <p>
     * A URL is considered more likely to be the canonical item URL if:
     * <ul>
     * <li>it contains the record identifier in its path part</li>
     * <li>it has the same domain part as the OAI-PMH base URL</li>
     * </ul>
     *
     * @param aURL The URL to score
     * @param aRecordIdentifier The identifier of the record in which the URL was found
     * @param aRepositoryURL The base URL from which the record was harvested
     * @return The score
     */
    static int scoreURL(final URL aURL, final String aRecordIdentifier, final URL aRepositoryURL) {
        int score = 0;
        final String importantIdentifierPart;

        if (isOaiIdentifier(aRecordIdentifier)) {
            importantIdentifierPart = aRecordIdentifier.split(COLON, 3)[2];
        } else {
            importantIdentifierPart = aRecordIdentifier;
        }

        if (aURL.getPath().contains(URLEncoder.encode(importantIdentifierPart, StandardCharsets.UTF_8))) {
            score++;
        }

        if (aURL.getHost().equals(aRepositoryURL.getHost())) {
            score++;
        }

        return score;
    }

    /**
     * @param aRecordIdentifier A record identifier
     * @return Whether or not the identifier follows the OAI identifier guidelines
     * @see <a href="http://www.openarchives.org/OAI/2.0/guidelines-oai-identifier.htm">identifier guidelines</a>
     */
    static boolean isOaiIdentifier(final String aRecordIdentifier) {
        final String[] components = aRecordIdentifier.split(COLON, 3);

        return components.length == 3 && "oai".equals(components[0]) && !components[1].equals(EMPTY) &&
                !components[2].equals(EMPTY);
    }

    /**
     * Finds an image URL, if any, out of the provided list of URLs.
     *
     * @param aPossibleImageUrls The list of URLs to try
     * @param aWebClient An HTTP client for checking URLs
     * @return The optional image URL
     */
    static Future<Optional<URL>> findImageURL(final List<URL> aPossibleImageUrls, final WebClient aWebClient) {
        @SuppressWarnings("rawtypes")
        final List<Future> contentTypeChecks = aPossibleImageUrls.stream().map(url -> {
            final HttpRequest<?> headRequest = aWebClient.headAbs(url.toString());

            return headRequest.send().compose(response -> {
                final String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE.toString());

                LOGGER.trace(MessageCodes.PRL_017, headRequest.method(), url, response.statusCode(), contentType);

                if (contentType.contains("image")) {
                    return Future.succeededFuture(url);
                } else {
                    return Future.failedFuture("not an image URL");
                }
            }, Future::failedFuture);
        }).collect(Collectors.toUnmodifiableList());

        return CompositeFuture.any(contentTypeChecks).map(result -> {
            // Scan the list for the first image URL
            for (final URL url : result.<URL>list()) {
                if (url != null) {
                    return Optional.of(url);
                }
            }
            return Optional.<URL>empty(); // By the semantics of CompositeFuture, this will never be reached
        }).recover(details -> {
            // It's okay if there is no thumbnail
            return Future.succeededFuture(Optional.<URL>empty());
        });
    }
}
