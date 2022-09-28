
package edu.ucla.library.prl.harvester;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

/**
 * Tests that the test database is setup correctly and accessible.
 */
@ExtendWith(VertxExtension.class)
public class DatabaseIT {

    /**
     * A logger for the DatabaseIT tests.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseIT.class, MessageCodes.BUNDLE);

    /**
     * Expected column names from the institutions table.
     */
    @SuppressWarnings("PMD.MultipleStringLiterals")
    private static final Set<String> INSTITUTION_COLUMNS =
            Set.of("id", "name", "description", "location", "email", "phone", "webcontact", "website");

    /**
     * Expected column names from the harvestjobs table.
     */
    @SuppressWarnings("PMD.MultipleStringLiterals")
    private static final Set<String> HARVESTJOB_COLUMNS = Set.of("id", "institutionid", "repositorybaseurl",
            "metadataprefix", "sets", "lastsuccessfulrun", "schedulecronexpression");

    /**
     * The query string used to test the database setup.
     */
    private static final String QUERY =
            "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = $1;";

    /**
     * The test database's client.
     */
    private static PgPool myDatabase;

    /**
     * Sets up the test database.
     */
    @BeforeAll
    public static final void setUp() {
        // Note: TRACE level logging should NEVER be used on a public server.
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(MessageCodes.PRL_005, System.getenv(Config.DB_USERNAME), System.getenv(Config.DB_PORT),
                    System.getenv(Config.DB_PASSWORD));
        } else {
            // The below still only triggers if DEBUG level requirement is met.
            LOGGER.debug(MessageCodes.PRL_005, System.getenv(Config.DB_USERNAME), System.getenv(Config.DB_PORT),
                    "****");
        }

        myDatabase = PgPool.pool();
    }

    /**
     * Clean up the database client.
     *
     * @param aContext A test context
     */
    @AfterAll
    public static final void tearDown(final VertxTestContext aContext) {
        aContext.assertComplete(myDatabase.close());
        aContext.completeNow();
    }

    /**
     * Tests the institutions database table.
     *
     * @param aContext A test context
     */
    final void testInstitutionsTable(final VertxTestContext aContext) {
        final Checkpoint checkpoint = aContext.checkpoint(INSTITUTION_COLUMNS.size());
        final PreparedQuery<RowSet<Row>> query = myDatabase.preparedQuery(QUERY);

        aContext.assertComplete(query.execute(Tuple.of("institutions"))).onComplete(aContext.succeeding(rowSet -> {
            aContext.verify(() -> {
                rowSet.forEach(row -> {
                    assertTrue(INSTITUTION_COLUMNS.contains(row.getString(0)));
                    checkpoint.flag();
                });
            });
        }));
    }

    /**
     * Tests the harvestjobs database table.
     *
     * @param aContext A test context
     */
    @Test
    final void testHarvestJobsTable(final VertxTestContext aContext) {
        final Checkpoint checkpoint = aContext.checkpoint(HARVESTJOB_COLUMNS.size());
        final PreparedQuery<RowSet<Row>> query = myDatabase.preparedQuery(QUERY);

        aContext.assertComplete(query.execute(Tuple.of("harvestjobs"))).onComplete(aContext.succeeding(rowSet -> {
            aContext.verify(() -> {
                rowSet.forEach(row -> {
                    assertTrue(HARVESTJOB_COLUMNS.contains(row.getString(0)));
                    checkpoint.flag();
                });
            });
        }));
    }
}
