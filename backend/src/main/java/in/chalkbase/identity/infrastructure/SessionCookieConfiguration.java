package in.chalkbase.identity.infrastructure;

import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pins the two flags that make the session cookie safe to hand a browser.
 *
 * <p>These are also expressible as {@code server.servlet.session.cookie.*}, and in production they
 * would be applied from there — but only there. Spring Boot builds the cookie serializer from those
 * properties when there is an embedded web server, and from the servlet container's own
 * {@code SessionCookieConfig} otherwise, which is what a MockMvc test and a WAR deployment both
 * look like. The result is a cookie that is {@code HttpOnly} in production and not in the tests
 * that are supposed to prove it, which is precisely backwards.
 *
 * <p>A {@link DefaultCookieSerializerCustomizer} runs last in both cases, so setting them here
 * makes the decision explicit and identical everywhere. {@code Secure} is deliberately left alone:
 * unset, Spring Session derives it from whether the request arrived over HTTPS, which is right in
 * production and does not silently break plain-HTTP local development.
 */
@Configuration
public class SessionCookieConfiguration {

    @Bean
    DefaultCookieSerializerCustomizer sessionCookieCustomizer() {
        return serializer -> {
            // Script must never be able to read the session id — that is most of what an XSS
            // becomes without it.
            serializer.setUseHttpOnlyCookie(true);
            // Lax, not None: a cross-site POST must not be able to ride the session. Lax rather
            // than Strict so a link into the app from an email still arrives signed in.
            serializer.setSameSite("Lax");
        };
    }
}
