package in.chalkbase.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pins {@code UserDetailsServiceAutoConfiguration} off, on the profile where its warning was found.
 *
 * <p>The {@code prod} profile is activated alongside {@code test} for the same reason
 * {@link SetupKeyFilterTests} does it: the datasource properties below exist only so
 * {@code application-prod.yml}'s {@code ${SPRING_DATASOURCE_URL}} placeholders resolve — the
 * connection actually used is the Testcontainers one, which {@code @ServiceConnection} supplies as a
 * {@code JdbcConnectionDetails} bean that takes precedence over any property. Nothing here reaches
 * Supabase.
 *
 * <p>Without the exclusion in {@code application.yml}, Boot auto-configures an
 * {@code InMemoryUserDetailsManager} — a {@link UserDetailsService} bean — and logs a fresh random
 * password for it on every boot. Nothing in this codebase ever asks a Spring Security
 * {@code AuthenticationManager} for a principal (see {@code AuthenticationService} and
 * {@code SecurityConfig}'s class javadoc), so that bean, if present, is unreachable by any request —
 * this test only pins it absent, so the boot warning cannot come back unnoticed.
 */
@SpringBootTest(
        properties = {
            "SPRING_DATASOURCE_URL=jdbc:postgresql://overridden-by-testcontainers/chalkbase",
            "SPRING_DATASOURCE_USERNAME=unused",
            "SPRING_DATASOURCE_PASSWORD=unused"
        })
@ActiveProfiles({"test", "prod"})
@Import(TestcontainersConfiguration.class)
class UserDetailsServiceAutoConfigurationTests {

    @Autowired
    ApplicationContext context;

    @Test
    void doesNotAutoConfigureAnInMemoryUserDetailsService() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }
}
