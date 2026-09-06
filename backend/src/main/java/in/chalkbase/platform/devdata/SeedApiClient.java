package in.chalkbase.platform.devdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to this application's own HTTP API, as a browser would.
 *
 * <p>Only {@link DemoSchoolSeeder} uses it, and the reason it exists at all is set out there: the
 * seeder has no legal way to import another module's request records, so it sends JSON instead. What
 * falls out of that is worth more than the workaround — every seeded row goes through the session
 * cookie, the CSRF check, the {@code @PreAuthorize} on the controller, the service's transaction and
 * the audit write, so a demo school that builds successfully is a demo school that has just
 * exercised the whole stack.
 *
 * <p>Cookies are kept in a jar for the life of one seed run, which is what carries the {@code SESSION}
 * cookie after sign-in. The CSRF token is read back out of that jar and echoed in
 * {@code X-XSRF-TOKEN}, exactly as the frontend does it — {@code SecurityConfig} issues the cookie on
 * every response and exempts only login and onboarding.
 *
 * <p><strong>No response body is ever logged.</strong> The bodies flowing through here are children's
 * names, dates of birth and admission numbers, all Confidential under ADR-0014. A failure reports the
 * status and the error <em>code</em> from the ADR-0007 envelope, which is a code and a sentence with
 * no values in it, and nothing else.
 */
final class SeedApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final URI base;
    private final CookieManager cookies;
    private final HttpClient http;

    SeedApiClient(int port) {
        this.base = URI.create("http://127.0.0.1:" + port);
        this.cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    JsonNode get(String path) {
        return send(HttpRequest.newBuilder(base.resolve(path)).GET(), path);
    }

    JsonNode post(String path, Map<String, Object> body) {
        return send(
                HttpRequest.newBuilder(base.resolve(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(serialise(body))),
                path);
    }

    /** The {@code data} of the ADR-0007 envelope, which is where every successful payload lives. */
    JsonNode postForData(String path, Map<String, Object> body) {
        return post(path, body).path("data");
    }

    private JsonNode send(HttpRequest.Builder request, String path) {
        csrfToken().ifPresent(token -> request.header(CSRF_HEADER, token));
        HttpResponse<String> response;
        try {
            response = http.send(request.timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("Demo seed could not reach " + path, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo seed was interrupted calling " + path, ex);
        }

        JsonNode body = parse(response.body(), path);
        if (response.statusCode() >= 400) {
            // The code and the message, never the body: an ADR-0007 error message names no value,
            // but the request that produced it was a child's record (ADR-0014).
            throw new IllegalStateException("Demo seed: " + path + " answered " + response.statusCode() + " "
                    + body.path("error").path("code").asText("(no error code)") + " — "
                    + body.path("error").path("message").asText("(no message)"));
        }
        return body;
    }

    private static JsonNode parse(String body, String path) {
        try {
            return JSON.readTree(body.isBlank() ? "{}" : body);
        } catch (IOException ex) {
            throw new IllegalStateException("Demo seed could not read the answer from " + path, ex);
        }
    }

    private static String serialise(Map<String, Object> body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Demo seed could not build a request body", ex);
        }
    }

    private Optional<String> csrfToken() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> CSRF_COOKIE.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst();
    }
}
