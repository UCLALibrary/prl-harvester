
package edu.ucla.library.prl.harvester.utils;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;
import edu.ucla.library.prl.harvester.Param;

import io.ino.solrs.JavaAsyncSolrClient;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.templates.SqlTemplate;
import io.vertx.uritemplate.Variables;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletionStage;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

import org.jeasy.random.randomizers.EmailRandomizer;
import org.jeasy.random.randomizers.Ipv4AddressRandomizer;
import org.jeasy.random.randomizers.RegularExpressionRandomizer;
import org.jeasy.random.randomizers.SentenceRandomizer;
import org.jeasy.random.randomizers.time.OffsetDateTimeRandomizer;

import org.quartz.CronExpression;

/**
 * Utilities related to working with test objects.
 */
public final class TestUtils {

    private static final Random RANDOMIZER = new Random();

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private static final String URL_PREFIX = "http://";

    private static final String CONTACT_POSTFIX = "/contact";

    private static final String QUESTION = "?";

    private static final EmailRandomizer RAND_EMAIL = new EmailRandomizer();

    private static final Ipv4AddressRandomizer RAND_URL = new Ipv4AddressRandomizer();

    private static final RegularExpressionRandomizer RAND_PHONE =
            new RegularExpressionRandomizer("^\\+1 310 [2-9]\\d{2} \\d{4}$");

    private static final SentenceRandomizer RAND_STRING = new SentenceRandomizer();

    private static final OffsetDateTimeRandomizer RAND_DATE = new OffsetDateTimeRandomizer();

    private static final String SOLR_SELECT_ALL = "*:*";

    private TestUtils() {
    }

    /**
     * Gets a random {@link Institution} for testing.
     *
     * @return A random Institution object
     */
    public static Institution getRandomInstitution()
            throws AddressException, MalformedURLException, NumberParseException {

        final String randName = RAND_STRING.getRandomValue();
        final String randDescription = RAND_STRING.getRandomValue();
        final String randLocation = RAND_STRING.getRandomValue();
        final Optional<InternetAddress> randEmail = Optional.of(new InternetAddress(RAND_EMAIL.getRandomValue()));
        final Optional<PhoneNumber> randPhone = Optional.of(PHONE_NUMBER_UTIL.parse(RAND_PHONE.getRandomValue(), null));
        final Optional<URL> randWebContact =
                Optional.of(new URL(URL_PREFIX.concat(RAND_URL.getRandomValue()).concat(CONTACT_POSTFIX)));
        final URL randWebsite = new URL(URL_PREFIX.concat(RAND_URL.getRandomValue()));

        return new Institution(randName, randDescription, randLocation, randEmail, randPhone, randWebContact,
                randWebsite);
    }

    /**
     * Gets a random {@link Job} for testing.
     *
     * @param anInstitutionID The ID of the institution to associate the job with
     * @return A random Job object
     */
    public static Job getRandomJob(final Integer anInstitutionID) throws MalformedURLException, ParseException {

        final int randListSize = RANDOMIZER.nextInt(5) + 1;
        final URL randURL = new URL(URL_PREFIX.concat(RAND_URL.getRandomValue()));
        final List<String> randSets = new ArrayList<>(randListSize);
        final OffsetDateTime randDate = RAND_DATE.getRandomValue();
        final CronExpression randCron = new CronExpression(buildCron(randDate));

        for (int index = 0; index < randListSize; index++) {
            randSets.add(RAND_STRING.getRandomValue().replaceAll("\\s", ""));
        }

        return new Job(anInstitutionID, randURL, randSets, randCron, null);
    }

    private static String buildCron(final OffsetDateTime aSourceDate) {
        final String blank = " ";
        final StringBuffer cronExpression = new StringBuffer();

        cronExpression.append(aSourceDate.getSecond()).append(blank);
        cronExpression.append(aSourceDate.getMinute()).append(blank);
        cronExpression.append(aSourceDate.getHour()).append(blank);
        cronExpression.append(aSourceDate.getDayOfMonth()).append(blank);
        cronExpression.append(aSourceDate.getMonthValue()).append(blank);
        cronExpression.append(QUESTION);
        return cronExpression.toString();
    }

    /**
     * Clears out the database.
     *
     * @param aConnectionPool A database connection pool
     * @return A Future that succeeds if the database was wiped successfully, and fails otherwise
     */
    public static Future<SqlResult<Void>> wipeDatabase(final Pool aConnectionPool) {
        return aConnectionPool.withConnection(connection -> {
            return SqlTemplate.forUpdate(connection, "TRUNCATE public.harvestjobs, public.institutions")
                    .execute(Map.of());
        });
    }

    /**
     * Clears out the Solr index.
     *
     * @param aSolrClient A Solr client
     * @return A Future that succeeds if the Solr index was wiped successfully, and fails otherwise
     */
    public static Future<UpdateResponse> wipeSolr(final JavaAsyncSolrClient aSolrClient) {
        final CompletionStage<UpdateResponse> wipeSolr =
                aSolrClient.deleteByQuery(SOLR_SELECT_ALL).thenCompose(result -> aSolrClient.commit());

        return Future.fromCompletionStage(wipeSolr);
    }

    /**
     * @param aSolrClient A Solr client
     * @return A Future that resolves to the list of all documents
     */
    public static Future<SolrDocumentList> getAllDocuments(final JavaAsyncSolrClient aSolrClient) {
        final SolrParams solrParams = new NamedList<>(Map.of("q", SOLR_SELECT_ALL)).toSolrParams();
        final CompletionStage<SolrDocumentList> results =
                aSolrClient.query(solrParams).thenApply(QueryResponse::getResults);

        return Future.fromCompletionStage(results);
    }

    /**
     * @param anID An ID
     * @return An ID used to expand a {@link UriTemplate}
     */
    public static Variables getUriTemplateVars(final int anID) {
        return Variables.variables(new JsonObject().put(Param.id.name(), anID));
    }
}
