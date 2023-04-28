
package edu.ucla.library.prl.harvester.handlers;

import static info.freelibrary.util.Constants.QUESTION_MARK;

import java.util.regex.Pattern;

import info.freelibrary.util.HTTP;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.prl.harvester.Config;
import edu.ucla.library.prl.harvester.MessageCodes;
import edu.ucla.library.prl.harvester.Paths;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.auth.authorization.AuthorizationProvider;
import io.vertx.ext.auth.ldap.LdapAuthnOptions;
import io.vertx.ext.auth.ldap.LdapAuthnProvider;
import io.vertx.ext.auth.ldap.LdapAuthzProvider;
import io.vertx.ext.auth.ldap.LdapRole;
import io.vertx.ext.web.RoutingContext;

/**
 * An authorization handler that confirms a user is authorized to access the requested content.
 */
public class AuthHandler implements Handler<RoutingContext> {

    /** The username property our form uses. */
    public static final String FORM_USERNAME = "username";

    /** The password property our form uses. */
    public static final String FORM_PASSWORD = "password";

    /** The AuthHandler's logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHandler.class, MessageCodes.BUNDLE);

    /** The handler's authentication provider. */
    private final AuthenticationProvider myAuthnProvider;

    /** The handler's authorization provider. */
    private final AuthorizationProvider myAuthzProvider;

    /**
     * Creates an authentication and authorization handler for the application.
     *
     * @param aVertx A Vert.x instance
     * @param aConfig A configuration
     */
    public AuthHandler(final Vertx aVertx, final JsonObject aConfig) {
        myAuthnProvider = LdapAuthnProvider.create(aVertx, getLdapConfig(aConfig));
        myAuthzProvider = LdapAuthzProvider.create(getLdapRole(aConfig));
    }

    @Override
    public void handle(final RoutingContext aContext) {
        final HttpServerRequest request = aContext.request();
        final String path = aContext.normalizedPath();

        LOGGER.debug(MessageCodes.PRL_040, path);

        // First check to see whether user is trying to login
        if (Paths.LOGIN.equals(path)) {
            final String username = request.getParam(FORM_USERNAME);
            final String password = request.getParam(FORM_PASSWORD);

            LOGGER.debug(MessageCodes.PRL_041, username);

            login(username, password).onFailure(aContext::fail).onSuccess(user -> {
                LOGGER.debug(MessageCodes.PRL_042, username);
                aContext.setUser(user);
                aContext.redirect(Paths.ADMIN);
            });
        } else {
            final User user = aContext.user();

            // If the user is visiting the logout link, log them out
            if (Paths.LOGOUT.equals(path) && user != null) {
                LOGGER.debug(MessageCodes.PRL_026, user.principal());
                aContext.clearUser();
            }

            // Check if user is authorized and allow them through if so; else, send forbidden response
            if (isAuthorized(aContext.user()) || Pattern.matches(Paths.ADMIN + QUESTION_MARK, path)) {
                LOGGER.debug(MessageCodes.PRL_027, path);
                aContext.next();
            } else {
                LOGGER.debug(MessageCodes.PRL_028, path);
                aContext.response().setStatusCode(HTTP.FORBIDDEN).end();
            }
        }
    }

    /**
     * Login the user and return an authenticated {@link User}.
     *
     * @param aUsername A username
     * @param aPassword A password
     * @return A future with a possible {@link User} result
     */
    private Future<User> login(final String aUsername, final String aPassword) {
        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials(aUsername, aPassword);
        final Promise<User> promise = Promise.promise();

        creds.checkValid(true); // Make sure there are no nulls (username or password) set

        // Attempt to authorize a user
        myAuthnProvider.authenticate(creds).compose(user -> {
            return myAuthzProvider.getAuthorizations(user).map(user);
        }).onSuccess(user -> {
            if (isAuthorized(user)) {
                promise.complete(user);
            } else {
                promise.fail(LOGGER.getMessage(MessageCodes.PRL_029, user.principal().encode()));
            }
        }).onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Checks whether the supplied user is authorized.
     *
     * @param aUser A site user
     * @return True if the user is authorized for access; else, false
     */
    private static boolean isAuthorized(final User aUser) {
        return aUser != null && aUser.authorizations().getProviderIds().contains(LdapAuthzProvider.DEFAULT_ID);
    }

    /**
     * Gets a configured LDAP role.
     *
     * @param aConfig A configuration
     * @return An LDAP role
     */
    private static LdapRole getLdapRole(final JsonObject aConfig) {
        return new LdapRole(aConfig.getString(Config.LDAP_ATTRIBUTE_KEY),
                aConfig.getString(Config.LDAP_ATTRIBUTE_VALUE));
    }

    /**
     * Gets the application's LDAP configuration.
     *
     * @param aConfig A configuration
     * @return An LDAP configuration
     */
    private static LdapAuthnOptions getLdapConfig(final JsonObject aConfig) {
        final LdapAuthnOptions ldapConfig = new LdapAuthnOptions();

        ldapConfig.setURL(aConfig.getString(Config.LDAP_URL));
        ldapConfig.setAuthenticationQuery(aConfig.getString(Config.LDAP_AUTH_QUERY));
        ldapConfig.setUserQuery(aConfig.getString(Config.LDAP_USER_QUERY),
                aConfig.getString(Config.LDAP_ATTRIBUTE_KEY));

        return ldapConfig;
    }
}
