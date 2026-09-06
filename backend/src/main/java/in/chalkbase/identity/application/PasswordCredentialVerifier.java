package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.UserCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PasswordCredentialVerifier.class);

    private final PasswordEncoder passwordEncoder;

    public PasswordCredentialVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public CredentialType supports() {
        return CredentialType.PASSWORD;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A stored secret with no algorithm prefix — a hand-written row, a half-finished import, a
     * credential format we no longer map — makes {@code DelegatingPasswordEncoder.matches} throw.
     * That is caught here and answered {@code false}, because the interface promises not to throw
     * and because the alternative is worse in three ways: the caller sees "something went wrong at
     * our end" for what is really a sign-in that cannot succeed, the attempt escapes the
     * failed-login audit and the lockout counter entirely, and a stack trace is logged on a path an
     * unauthenticated caller can reach at will.
     *
     * <p>The warning names the credential and never the secret. Found by running against the dev
     * database with a hash inserted by hand, which is exactly how it would first happen in
     * production.
     */
    @Override
    public boolean verify(UserCredential credential, String presented) {
        if (credential == null || credential.getSecret() == null || presented == null) {
            return false;
        }
        try {
            return passwordEncoder.matches(presented, credential.getSecret());
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Credential {} cannot be verified: its stored secret is not in a format this build maps."
                            + " Treating the sign-in as failed.",
                    credential.getId());
            return false;
        }
    }
}
