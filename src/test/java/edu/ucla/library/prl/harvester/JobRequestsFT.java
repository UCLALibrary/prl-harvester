
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;

import javax.mail.internet.AddressException;

import org.apache.http.HttpStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.i18n.phonenumbers.NumberParseException;

import edu.ucla.library.prl.harvester.services.HarvestScheduleStoreService;
import edu.ucla.library.prl.harvester.utils.TestUtils;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Pool;
import io.vertx.uritemplate.UriTemplate;

/**
 * Tests the application's behavior in response to requests involving {@link Job}s.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class JobRequestsFT {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobRequestsFT.class, MessageCodes.BUNDLE);

    private static final UriTemplate INSTITUTIONS = UriTemplate.of("/institutions");

    private static final UriTemplate JOBS = UriTemplate.of("/jobs");

    private Pool myDbConnectionPool;

    private WebClient myWebClient;

    private int myInstitutionID;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @BeforeAll
    public final void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        ConfigRetriever.create(aVertx).getConfig().compose(config -> {
            final String host = config.getString(Config.HTTP_HOST);
            final int port = config.getInteger(Config.HTTP_PORT);

            myDbConnectionPool = HarvestScheduleStoreService.getConnectionPool(aVertx, config);
            myWebClient = WebClient.create(aVertx, new WebClientOptions().setDefaultHost(host).setDefaultPort(port));

            return Future.succeededFuture();
        }).onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     * @throws AddressException
     * @throws MalformedURLException
     * @throws NumberParseException
     */
    @BeforeEach
    public void beforeEach(final Vertx aVertx, final VertxTestContext aContext)
            throws AddressException, MalformedURLException, NumberParseException {
        myWebClient.post(INSTITUTIONS).sendJson(TestUtils.getRandomInstitution().toJson()).onSuccess(response -> {
            myInstitutionID = new Institution(response.bodyAsJsonObject()).getID().get();

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterEach
    public void afterEach(final Vertx aVertx, final VertxTestContext aContext) {
        TestUtils.wipeDatabase(myDbConnectionPool).onSuccess(nil -> aContext.completeNow())
                .onFailure(aContext::failNow);
    }

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @AfterAll
    public final void tearDown(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.close();
        myDbConnectionPool.close().onSuccess(result -> aContext.completeNow()).onFailure(aContext::failNow);
    }

    /**
     * Tests that {@link Op#listJobs} initially retrieves an empty list.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A test context
     */
    @Test
    void testListBeforeAdd(final Vertx aVertx, final VertxTestContext aContext) {
        myWebClient.get(JOBS).send().onSuccess(response -> {
            aContext.verify(() -> {
                assertEquals(HttpStatus.SC_OK, response.statusCode());
                assertTrue(response.bodyAsJsonArray().isEmpty());
            }).completeNow();
        }).onFailure(aContext::failNow);
    }
}
