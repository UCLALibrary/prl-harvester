
package edu.ucla.library.prl.harvester;

import static edu.ucla.library.prl.harvester.handlers.AuthHandler.FORM_PASSWORD;
import static edu.ucla.library.prl.harvester.handlers.AuthHandler.FORM_USERNAME;

import java.util.List;

import info.freelibrary.util.HTTP;
import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.uritemplate.UriTemplate;

/**
 * A base test class that adds authorization support to the functional tests that extend it.
 */
class AuthorizedFIT {

    /** A test logger. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizedFIT.class, MessageCodes.BUNDLE);

    /** The login URL template. */
    private static UriTemplate LOGIN_URL = UriTemplate.of("/login");

    /** The name of the Vert.x session cookie. */
    private static final String COOKIE_NAME = "vertx-web.session";

    /**
     * Tries to authorize a new WebClient interaction. The {@link WebClient} passed here must be created with a
     * {@link WebClientSession}.
     *
     * @param aWebClient An unauthorized Web client
     * @return A successful future with an authorized Web client or a failed future if the authorization failed
     */
    protected Future<WebClient> authorize(final WebClient aWebClient) {
        final Promise<WebClient> promise = Promise.promise();
        final String username = System.getenv(Config.LDAP_USERNAME);
        final String password = System.getenv(Config.LDAP_PASSWORD);
        final HttpRequest<Buffer> request = aWebClient.post(LOGIN_URL);

        // Mimic our login form's user information
        request.addQueryParam(FORM_USERNAME, username).addQueryParam(FORM_PASSWORD, password);

        // Send the login request
        request.send().onSuccess(response -> {
            final List<String> cookies = response.cookies();

            // Check that the WebClient has been authorized
            if (authorized(response.statusCode(), response.getHeader(HttpHeaders.LOCATION.toString()), cookies)) {
                promise.complete(aWebClient);
            } else {
                promise.fail(LOGGER.getMessage(MessageCodes.PRL_043));
            }
        }).onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Checks whether a session has been authorized by the HTTP status code and Location header.
     *
     * @param aStatusCode An HTTP status code
     * @param aLocation A <code>Location</code> header
     * @param aCookieList A list of cookie strings
     * @return True if the client is authorized; else, false
     */
    private boolean authorized(final int aStatusCode, final String aLocation, final List<String> aCookieList) {
        return aStatusCode == HTTP.FOUND && Paths.ADMIN.equals(aLocation) &&
                aCookieList.stream().filter(cookie -> cookie.startsWith(COOKIE_NAME)).findFirst().isPresent();
    }
}
