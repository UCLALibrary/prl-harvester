
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.mail.internet.AddressException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.quartz.CronExpression;

import com.google.i18n.phonenumbers.NumberParseException;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.Pool;

/**
 * Tests {@link HarvestService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class HarvestServiceIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestServiceIT.class, MessageCodes.BUNDLE);

    private MessageConsumer<JsonObject> myHarvestScheduleStoreService;

    private HarvestScheduleStoreService myHarvestScheduleStoreServiceProxy;

    private MessageConsumer<JsonObject> myHarvestService;

    private HarvestService myHarvestServiceProxy;

    private Pool myDbConnectionPool;

    private JavaAsyncSolrClient mySolrClient;

    private URL myTestProviderBaseURL;

    private Integer myTestInstitutionID;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final Institution testInstitution;
            final Pool dbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            final HarvestScheduleStoreService scheduleStoreService =
                    HarvestScheduleStoreService.create(aVertx, dbConnectionPool);
            final HarvestService service = HarvestService.create(aVertx, config);
            final ServiceBinder binder = new ServiceBinder(aVertx);

            myHarvestScheduleStoreService = binder.setAddress(HarvestScheduleStoreService.ADDRESS)
                    .register(HarvestScheduleStoreService.class, scheduleStoreService);
            myHarvestScheduleStoreServiceProxy = HarvestScheduleStoreService.createProxy(aVertx);

            myHarvestService = binder.setAddress(HarvestService.ADDRESS).register(HarvestService.class, service);
            myHarvestServiceProxy = HarvestService.createProxy(aVertx, config);

            myDbConnectionPool = dbConnectionPool;
            mySolrClient = JavaAsyncSolrClient.create(config.getString(Config.SOLR_CORE_URL));

            try {
                myTestProviderBaseURL = new URL(config.getString(TestUtils.TEST_PROVIDER_BASE_URL));
                testInstitution = TestUtils.getRandomInstitution();
            } catch (final AddressException | MalformedURLException | NumberParseException details) {
                return Future.failedFuture(details);
            }

            return myHarvestScheduleStoreServiceProxy.addInstitutions(List.of(testInstitution))
                    .compose(institutions -> {
                        myTestInstitutionID = TestUtils.unwrapInstitutionID(institutions.get(0));

                        return Future.succeededFuture();
                    });
        }).onSuccess(nil -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterEach
    public void afterEach(final Vertx aVertx, final VertxTestContext aContext) {
        myHarvestScheduleStoreServiceProxy.getInstitution(myTestInstitutionID).compose(institution -> {
            return TestUtils.removeItemRecords(mySolrClient, institution.getName());
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        TestUtils.wipeSolr(mySolrClient).compose(result -> {
            return myHarvestServiceProxy.close();
        }).compose(result -> {
            mySolrClient.shutdown();

            return TestUtils.wipeDatabase(myDbConnectionPool).compose(nil -> myDbConnectionPool.close());
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Tests the harvesting of various jobs that should succeed.
     *
     * @param aSets The list of sets to harvest; if empty, assume all sets should be harvested
     * @param aScheduleCronExpression The schedule on which this job should be run
     * @param aLastSuccessfulRun The timestamp of the last successful run of this job; will be null at first
     * @param anExpectedRecordCount The expected number of records that would be harvested by the job
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @ParameterizedTest
    @MethodSource
    @Timeout(value = 1, timeUnit = TimeUnit.MINUTES)
    public void testRun(final List<String> aSets, final CronExpression aScheduleCronExpression,
            final OffsetDateTime aLastSuccessfulRun, final int anExpectedRecordCount, final Vertx aVertx,
            final VertxTestContext aContext) {
        final Job job = Job.withID(
                new Job(myTestInstitutionID, myTestProviderBaseURL, aSets, aScheduleCronExpression, aLastSuccessfulRun),
                1);

        myHarvestServiceProxy.run(job).onSuccess(jobResult -> {
            TestUtils.getAllDocuments(mySolrClient).onSuccess(queryResults -> {
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
     * @throws ParseException
     */
    Stream<Arguments> testRun() throws ParseException {
        // The schedule is irrelevant here, but we need something to instantiate Jobs with
        final String set1 = "set1";
        final String set2 = "set2";
        final CronExpression schedule = new CronExpression("* * * * * ?");

        // These arguments reflect the directory structure of src/test/resources/provider
        return Stream.of( //
                Arguments.of(List.of(set1), schedule, null, 2), //
                Arguments.of(List.of(set2), schedule, null, 3), //
                Arguments.of(List.of(set1, set2), schedule, null, 5), //
                Arguments.of(List.of(), schedule, null, 5), //
                Arguments.of(List.of("undefined"), schedule, null, 0), //
                Arguments.of(List.of(set1, "nil"), schedule, null, 2), //
                Arguments.of(null, schedule, OffsetDateTime.now().minusHours(1), 5), //
                Arguments.of(null, schedule, OffsetDateTime.now().plusHours(1), 0));
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
            TestUtils.getAllDocuments(mySolrClient).onSuccess(queryResults -> {
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
        // The schedule is irrelevant here, but we need something to instantiate Jobs
        // with
        final URL baseURL = new URL("https://digital.library.ucla.edu/catalog/oai");
        final String huxley = "member_of_collection_ids_ssim:2zv35200zz-89112";
        final CronExpression schedule = new CronExpression("0 * * * * ?");

        return Stream.of( //
                Arguments.of(Job.withID(new Job(1, baseURL, List.of(huxley), schedule, null), 1)));
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
        final Job job = Job.withID(new Job(myTestInstitutionID, new URL("http://example.com"), null,
                new CronExpression("0 0 * * * ?"), null), 1);

        myHarvestServiceProxy.run(job).onFailure(details -> {
            LOGGER.debug(details.toString());

            aContext.completeNow();
        }).onSuccess(result -> {
            aContext.failNow(LOGGER.getMessage(MessageCodes.PRL_000, result.toJson()));
        });
    }
}
