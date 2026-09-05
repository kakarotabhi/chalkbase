package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.UserCredential;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password proof, delegated to Spring Security's {@code DelegatingPasswordEncoder}.
 *
 * <p>The stored hash carries its own algorithm prefix, so the hashing algorithm can be upgraded by
 * re-hashing on the next successful login rather than by a migration (ADR-0003).
 */
@Component
public class PasswordCredentialVerifier implements CredentialVerifier {

    private final PasswordEncoder passwordEncoder;

    public PasswordCredentialVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public CredentialType supports() {
        return CredentialType.PASSWORD;
    }

    @Override
    public boolean verify(UserCredential credential, String presented) {
        if (credential == null || credential.getSecret() == null || presented == null) {
            return false;
        }
        return passwordEncoder.matches(presented, credential.getSecret());
    }
}
