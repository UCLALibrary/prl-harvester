
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgPool;
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

    private PgPool myDbConnectionPool;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().onSuccess(config -> {
            final PgPool dbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            final ServiceBinder binder = new ServiceBinder(aVertx);

            myTestProviderBaseURL = config.getString(Config.TEST_PROVIDER_BASE_URL);
            mySolrClient = JavaAsyncSolrClient.create(config.getString(Config.SOLR_CORE_URL));

            myHarvestService = binder.setAddress(HarvestService.ADDRESS).register(HarvestService.class,
                    HarvestService.create(aVertx, config));
            myHarvestServiceProxy = HarvestService.createProxy(aVertx, config);
            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, HarvestScheduleStoreService.create(dbConnectionPool));

            myDbConnectionPool = dbConnectionPool;

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeEach
    public void beforeEach(final Vertx aVertx, final VertxTestContext aContext) {
        wipeSolr().onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Clears out the Solr index.
     *
     * @return A Future that succeeds if the Solr index was wiped successfully, and fails otherwise
     */
    private Future<UpdateResponse> wipeSolr() {
        final CompletionStage<UpdateResponse> wipeSolr =
                mySolrClient.deleteByQuery(SOLR_SELECT_ALL).thenCompose(result -> mySolrClient.commit());

        return Future.fromCompletionStage(wipeSolr);
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
            getAllDocuments(mySolrClient).onSuccess(queryResults -> {
                LOGGER.debug(queryResults.toString());

                aContext.verify(() -> {
                    // Check that the two counts agree
                    assertEquals(anExpectedRecordCount, jobResult.getRecordCount());
                    assertEquals(anExpectedRecordCount, queryResults.getNumFound());
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
                Arguments.of(new Job(1, baseURL, List.of(set1), schedule, null), 2), //
                Arguments.of(new Job(1, baseURL, List.of(set2), schedule, null), 3), //
                Arguments.of(new Job(1, baseURL, List.of(set1, set2), schedule, null), 5), //
                Arguments.of(new Job(1, baseURL, List.of(), schedule, null), 5), //
                Arguments.of(new Job(1, baseURL, List.of("undefined"), schedule, null), 0), //
                Arguments.of(new Job(1, baseURL, List.of(set1, "nil"), schedule, null), 2), //
                Arguments.of(new Job(1, baseURL, null, schedule, OffsetDateTime.now().minusHours(1)), 5), //
                Arguments.of(new Job(1, baseURL, null, schedule, OffsetDateTime.now().plusHours(1)), 0));
    }

    /**
     * Tests that the harvesting of production OAI-PMH data providers succeeds.
     *
     * @param aJob A harvest job
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @MethodSource
    @Tag("real-provider")
    @Timeout(value = 5, timeUnit = TimeUnit.MINUTES)
    public void testRunRealProvider(final Job aJob, final Vertx aVertx, final VertxTestContext aContext) {
        myHarvestServiceProxy.run(aJob).onSuccess(jobResult -> {
            getAllDocuments(mySolrClient).onSuccess(queryResults -> {
                LOGGER.debug(queryResults.toString());

                aContext.verify(() -> {
                    assertEquals(jobResult.getRecordCount(), queryResults.getNumFound());
                }).completeNow();
            }).onFailure(aContext::failNow);
        }).onFailure(aContext::failNow);
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     * @throws MalformedURLException
     * @throws ParseException
     */
    Stream<Arguments> testRunRealProvider() throws MalformedURLException, ParseException {
        // The schedule is irrelevant here, but we need something to instantiate Jobs with
        final URL baseURL = new URL("https://digital.library.ucla.edu/catalog/oai");
        final String huxley = "member_of_collection_ids_ssim:2zv35200zz-89112";
        final CronExpression schedule = new CronExpression("0 * * * * ?");

        return Stream.of( //
                Arguments.of(new Job(1, baseURL, List.of(huxley), schedule, null)));
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
                myHarvestServiceProxy.close().compose(nil -> myHarvestService.unregister());
        final Future<Void> closeSolr = wipeSolr().compose(result -> {
            mySolrClient.shutdown();

            return Future.succeededFuture();
        });

        CompositeFuture.all(closeHarvestService.compose(nil -> myHarvestScheduleStoreService.unregister()), closeSolr)
                .compose(result -> myDbConnectionPool.close()).onSuccess(result -> aContext.completeNow())
                .onFailure(aContext::failNow);
    }

    /**
     * @param aSolrClient A Solr client
     * @return A Future that resolves to the list of all documents
     */
    private static Future<SolrDocumentList> getAllDocuments(final JavaAsyncSolrClient aSolrClient) {
        final CompletionStage<SolrDocumentList> results;
        final NamedList<String> solrParams = new NamedList<>();

        solrParams.add("q", SOLR_SELECT_ALL);

        results = aSolrClient.query(solrParams.toSolrParams()).thenApply(QueryResponse::getResults);

        return Future.fromCompletionStage(results);
    }
}
