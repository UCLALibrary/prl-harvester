
package edu.ucla.library.prl.harvester;

import static edu.ucla.library.prl.harvester.handlers.AuthHandler.FORM_USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import info.freelibrary.util.StringUtils;

import edu.ucla.library.prl.harvester.utils.TestUtils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.auth.ldap.LdapAuthnOptions;
import io.vertx.ext.auth.ldap.LdapAuthnProvider;
import io.vertx.ext.auth.ldap.LdapAuthzProvider;
import io.vertx.ext.auth.ldap.LdapRole;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests of the LDAP configuration.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class LdapAuthIT {

    /** Authentication options for our testing environment. */
    private LdapAuthnOptions myAuthOpts;

    /** The test user's username. */
    private String myUsername;

    /** The test user's password. */
    private String myPassword;

    /** The test user query. */
    private String myUserQuery;

    /**
     * @param aVertx A Vert.x instance
     * @param aContext A testing context
     */
    @BeforeAll
    public void setUp(final Vertx aVertx, final VertxTestContext aContext) {
        Config.getConfig(aVertx).onSuccess(config -> {
            myAuthOpts = new LdapAuthnOptions().setURL(config.getString(Config.LDAP_URL))
                    .setAuthenticationQuery(config.getString(Config.LDAP_AUTH_QUERY));
            myUsername = config.getString(TestUtils.LDAP_USERNAME);
            myPassword = config.getString(TestUtils.LDAP_PASSWORD);
            myUserQuery = config.getString(Config.LDAP_USER_QUERY);

            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * A test that confirms the Vert.x LDAP client can connect to the LDAP container.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A testing context
     */
    @Test
    public void testLdapConnection(final Vertx aVertx, final VertxTestContext aContext) {
        final AuthenticationProvider auth = LdapAuthnProvider.create(aVertx, myAuthOpts);

        // Testing password, used during container-tested build, is hard-coded (for now?)
        auth.authenticate(new UsernamePasswordCredentials(myUsername, myPassword)).onSuccess(user -> {
            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * A test that confirms LDAP user authentication works.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A testing context
     */
    @Test
    public void testLdapUser(final Vertx aVertx, final VertxTestContext aContext) {
        final AuthenticationProvider auth =
                LdapAuthnProvider.create(aVertx, myAuthOpts.setUserQuery(myUserQuery, new ArrayList<>()));

        auth.authenticate(new UsernamePasswordCredentials(myUsername, myPassword)).onSuccess(user -> {
            final JsonObject principal = user.principal();

            aContext.verify(() -> {
                assertEquals(principal.getString(FORM_USERNAME), myUsername);
                assertEquals(principal.getJsonArray("amr").getString(0), "pwd");
            }).completeNow();
        }).onFailure(aContext::failNow);
    }

    /**
     * A test that confirms LDAP user authorization works.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A testing context
     */
    @Test
    public void testLdapAuthentication(final Vertx aVertx, final VertxTestContext aContext) {
        final AuthenticationProvider auth =
                LdapAuthnProvider.create(aVertx, myAuthOpts.setUserQuery(myUserQuery, new ArrayList<>()));

        auth.authenticate(new UsernamePasswordCredentials(myUsername, myPassword)).onSuccess(user -> {
            final LdapAuthzProvider authorization = LdapAuthzProvider.create(new LdapRole(FORM_USERNAME, myUsername));

            authorization.getAuthorizations(user).onFailure(aContext::failNow).onSuccess(check -> {
                final Set<String> providerIDs = user.authorizations().getProviderIds();
                final String role = StringUtils.format("{}:{}", FORM_USERNAME, myUsername);

                aContext.verify(() -> {
                    assertEquals(1, providerIDs.size());
                    assertTrue(providerIDs.contains(LdapAuthzProvider.DEFAULT_ID));
                    assertTrue(RoleBasedAuthorization.create(role).match(user));
                }).completeNow();
            });
        }).onFailure(aContext::failNow);
    }
}
