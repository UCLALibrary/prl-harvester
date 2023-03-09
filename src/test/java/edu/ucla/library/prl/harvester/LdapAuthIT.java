
package edu.ucla.library.prl.harvester;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.auth.ldap.LdapAuthentication;
import io.vertx.ext.auth.ldap.LdapAuthenticationOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests of the LDAP configuration.
 */
@ExtendWith(VertxExtension.class)
public class LdapAuthIT {

    /**
     * A logger for the LdapAuthIT tests.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(LdapAuthIT.class, MessageCodes.BUNDLE);

    /**
     * A test that confirms the Vert.x LDAP client can connect to the LDAP container.
     *
     * @param aVertx A Vert.x instance
     * @param aContext A testing context
     */
    @Test
    public void testLdapConnection(final Vertx aVertx, final VertxTestContext aContext) {
        final String ldapURL = System.getenv(Config.LDAP_URL); // If null, auth creation will fail
        final LdapAuthenticationOptions ldapOpts = new LdapAuthenticationOptions().setUrl(ldapURL)
                .setAuthenticationQuery("cn={0},ou=prlgroup,dc=glauth,dc=com");
        final AuthenticationProvider auth = LdapAuthentication.create(aVertx, ldapOpts);

        // Testing password, used during container-tested build, is hard-coded (for now?)
        auth.authenticate(new UsernamePasswordCredentials("prl", "pearl")).onSuccess(user -> {
            // Just a "hello world" test for now...
            aContext.completeNow();
        }).onFailure(aContext::failNow);
    }
}
