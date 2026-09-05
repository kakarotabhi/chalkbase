package in.chalkbase.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline HTTP security.
 *
 * <p>TODO(identity): replace {@code permitAll} on /api/** with JWT resource-server auth and
 * method-level permission checks once the identity module lands. Tracked in
 * docs/architecture/adr/0003-authentication-and-authorization.md.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
