package in.chalkbase.identity.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password encoder for the whole application.
 *
 * <p>{@code createDelegatingPasswordEncoder()} stores the algorithm as a prefix inside the hash —
 * {@code {bcrypt}$2a$10$...} — which is what makes an algorithm upgrade a re-hash on next login
 * rather than a migration (ADR-0003). Never replace it with a bare {@code BCryptPasswordEncoder}:
 * that throws away the prefix and with it the upgrade path.
 */
@Configuration
public class PasswordEncoderConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
