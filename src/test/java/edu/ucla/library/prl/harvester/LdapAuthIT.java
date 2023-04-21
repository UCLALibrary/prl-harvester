
package edu.ucla.library.prl.harvester;

import static edu.ucla.library.prl.harvester.handlers.AuthHandler.FORM_USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.Test;
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
public class LdapAuthIT {

    /** Authentication options for our testing environment. */
    private static final LdapAuthnOptions AUTH_OPTS = new LdapAuthnOptions().setURL(System.getenv(Config.LDAP_URL))
            .setAuthenticationQuery(System.getenv(Config.LDAP_AUTH_QUERY));

    /** The test user's username. */
    private static final String USERNAME = System.getenv(TestUtils.LDAP_USERNAME);

    /** The test user's password. */
    private static final String PASSWORD = System.getenv(TestUtils.LDAP_PASSWORD);

    /**
     * A test that confirms the Vert.x LDAP client can connect to the LDAP container.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A testing context
     */
    @Test
    public void testLdapConnection(final Vertx aVertx, final VertxTestContext aContext) {
        final AuthenticationProvider auth = LdapAuthnProvider.create(aVertx, AUTH_OPTS);

        // Testing password, used during container-tested build, is hard-coded (for now?)
        auth.authenticate(new UsernamePasswordCredentials(USERNAME, PASSWORD)).onSuccess(user -> {
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
        final AuthenticationProvider auth = LdapAuthnProvider.create(aVertx,
                AUTH_OPTS.setUserQuery(System.getenv(Config.LDAP_USER_QUERY), new ArrayList<>()));

        auth.authenticate(new UsernamePasswordCredentials(USERNAME, PASSWORD)).onSuccess(user -> {
            final JsonObject principal = user.principal();

            aContext.verify(() -> {
                assertEquals(principal.getString(FORM_USERNAME), USERNAME);
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
        final AuthenticationProvider auth = LdapAuthnProvider.create(aVertx,
                AUTH_OPTS.setUserQuery(System.getenv(Config.LDAP_USER_QUERY), new ArrayList<>()));

        auth.authenticate(new UsernamePasswordCredentials(USERNAME, PASSWORD)).onSuccess(user -> {
            final LdapAuthzProvider authorization = LdapAuthzProvider.create(new LdapRole(FORM_USERNAME, USERNAME));

            authorization.getAuthorizations(user).onFailure(aContext::failNow).onSuccess(check -> {
                final Set<String> providerIDs = user.authorizations().getProviderIds();
                final String role = StringUtils.format("{}:{}", FORM_USERNAME, USERNAME);

                aContext.verify(() -> {
                    assertEquals(1, providerIDs.size());
                    assertTrue(providerIDs.contains(LdapAuthzProvider.DEFAULT_ID));
                    assertTrue(RoleBasedAuthorization.create(role).match(user));
                }).completeNow();
            });
        }).onFailure(aContext::failNow);
    }
}
