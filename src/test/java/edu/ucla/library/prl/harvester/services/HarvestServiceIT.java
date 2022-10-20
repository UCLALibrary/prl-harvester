
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.util.NamedList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.quartz.CronExpression;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;

/**
 * Tests {@link HarvestService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class HarvestServiceIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestServiceIT.class, MessageCodes.BUNDLE);

    private static final String SOLR_SELECT_ALL = "*:*";

    private MessageConsumer<JsonObject> myHarvestService;

    private HarvestService myHarvestServiceProxy;

    private JavaAsyncSolrClient mySolrClient;

    private MessageConsumer<JsonObject> myHarvestScheduleStoreService;

    private String myTestProviderBaseURL;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().onSuccess(config -> {
            final ServiceBinder binder = new ServiceBinder(aVertx);

            myTestProviderBaseURL = config.getString(Config.TEST_PROVIDER_BASE_URL);
            mySolrClient = JavaAsyncSolrClient.create(config.getString(Config.SOLR_CORE_URL));

            myHarvestService = binder.setAddress(HarvestService.ADDRESS).register(HarvestService.class,
                    HarvestService.create(aVertx, config));
            myHarvestServiceProxy = HarvestService.createProxy(aVertx);
            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, HarvestScheduleStoreService.create(aVertx, config));

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeEach
    public void beforeEach(final Vertx aVertx, final VertxTestContext aContext) {
        wipeSolr().onSuccess(unused -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Clears out the Solr index.
     *
     * @return A Future that succeeds if the Solr index was wiped successfully, and fails otherwise
     */
    private Future<Void> wipeSolr() {
        final CompletionStage<UpdateResponse> wipeSolr =
                mySolrClient.deleteByQuery(SOLR_SELECT_ALL).thenCompose(unused -> mySolrClient.commit());

        return Future.fromCompletionStage(wipeSolr).mapEmpty();
    }

    /**
     * Tests the harvesting of various jobs that should succeed.
     *
     * @param aJob A harvest job
     * @param anExpectedRecordCount The expected number of records that would be harvested by the job
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @MethodSource
    public void testRun(final Job aJob, final int anExpectedRecordCount, final Vertx aVertx,
            final VertxTestContext aContext) {
        myHarvestServiceProxy.run(aJob).onSuccess(jobResult -> {
            final CompletionStage<QueryResponse> query;
            final NamedList<String> solrParams = new NamedList<>();

            // Matches all documents
            solrParams.add("q", SOLR_SELECT_ALL);

            query = mySolrClient.query(solrParams.toSolrParams());

            Future.fromCompletionStage(query).onSuccess(queryResponse -> {
                LOGGER.debug(queryResponse.toString());

                aContext.verify(() -> {
                    // Check that the two counts agree
                    assertEquals(anExpectedRecordCount, jobResult.getRecordCount());
                    assertEquals(anExpectedRecordCount, queryResponse.getResults().size());
                }).completeNow();
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws MalformedURLException
     * @throws ParseException
     */
    Stream<Arguments> testRun() throws MalformedURLException, ParseException {
        // The schedule is irrelevant here, but we need something to instantiate Jobs with
        final URL baseURL = new URL(myTestProviderBaseURL);
        final String set1 = "set1";
        final String set2 = "set2";
        final CronExpression schedule = new CronExpression("* * * * * ?");

        // These arguments reflect the directory structure of src/test/resources/provider
        return Stream.of( //
                Arguments.of(new Job(1, baseURL, List.of(set1), schedule, null), 1), //
                Arguments.of(new Job(2, baseURL, List.of(set2), schedule, null), 3), //
                Arguments.of(new Job(3, baseURL, List.of(set1, set2), schedule, null), 4), //
                Arguments.of(new Job(4, baseURL, List.of(), schedule, null), 4), //
                Arguments.of(new Job(5, baseURL, List.of("undefined"), schedule, null), 0), //
                Arguments.of(new Job(6, baseURL, List.of(set1, "nil"), schedule, null), 1), //
                Arguments.of(new Job(7, baseURL, null, schedule, ZonedDateTime.now().minusHours(1)), 4), //
                Arguments.of(new Job(8, baseURL, null, schedule, ZonedDateTime.now().plusHours(1)), 0));
    }

    /**
     * Tests that a harvest job fails if the base URL is not that of an OAI-PMH data provider.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @throws ParseException
     * @throws MalformedURLException
     */
    public void testRunInvalidbaseURL(final Vertx aVertx, final VertxTestContext aContext)
            throws MalformedURLException, ParseException {
        final Job job = new Job(1, new URL("http://example.com"), null, new CronExpression("0 0 * * * ?"), null);

        myHarvestServiceProxy.run(job).onFailure(details -> {
            LOGGER.debug(details.toString());

            aContext.completeNow();
        }).onSuccess(result -> {
            aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_000, result.toJson()));
        });
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        final Future<Void> closeHarvestService =
                myHarvestServiceProxy.close().compose(unused -> myHarvestService.unregister());
        final Future<Void> closeSolr = wipeSolr().compose(unused -> {
            mySolrClient.shutdown();

            return Future.succeededFuture();
        });

        CompositeFuture
                .all(closeHarvestService.compose(unused -> myHarvestScheduleStoreService.unregister()), closeSolr)
                .onSuccess(unused -> aContext.completeNow()).onFailure(aContext::failNow);
    }
}
