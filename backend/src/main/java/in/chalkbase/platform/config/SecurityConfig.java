package in.chalkbase.platform.config;

import in.chalkbase.platform.error.SecurityErrorResponder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline HTTP security.
 *
 * <p>TODO(identity): replace {@code permitAll} with real authentication and method-level permission
 * checks when the identity module lands — see ADR-0003 and ADR-0005. Until then this application
 * must not be exposed publicly.
 *
 * <p>The 401 and 403 responders are wired now, before there is anything to authenticate, so those
 * two responses use the same envelope as everything else the moment they start firing.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, SecurityErrorResponder securityErrors) throws Exception {
        return http.csrf(csrf -> csrf.disable()) // TODO(identity): enable with cookie sessions (ADR-0003)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(securityErrors).accessDeniedHandler(securityErrors))
                .build();
    }
}
