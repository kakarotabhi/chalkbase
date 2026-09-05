package in.chalkbase.identity.domain;

/** Revoked credentials are kept rather than deleted, so a password change leaves an audit trail. */
public enum CredentialStatus {
    ACTIVE,
    REVOKED
}
