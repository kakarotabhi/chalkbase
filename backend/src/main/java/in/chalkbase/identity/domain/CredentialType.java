package in.chalkbase.identity.domain;

/** How a person proves who they are. One {@code CredentialVerifier} per constant. */
public enum CredentialType {
    PASSWORD,
    OTP,
    OIDC
}
