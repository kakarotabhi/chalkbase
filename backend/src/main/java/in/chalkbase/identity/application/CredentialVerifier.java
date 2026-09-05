package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.UserCredential;

/**
 * Checks one kind of proof.
 *
 * <p>This interface existing from day one is the point of ADR-0003: phone + OTP, an OIDC login or a
 * passkey each arrive as a new implementation and a new endpoint. Sessions, permissions and every
 * module that merely consumes a session stay untouched, and nothing in the schema has to move.
 *
 * <p>An implementation must be constant-time enough not to leak whether a secret exists, and must
 * never log the presented value or the stored one.
 */
public interface CredentialVerifier {

    /** The credential type this verifier handles. Exactly one verifier per type. */
    CredentialType supports();

    /** Whether {@code presented} proves {@code credential}. Never throws for a wrong secret. */
    boolean verify(UserCredential credential, String presented);
}
