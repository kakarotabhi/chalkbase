package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new account, issued a generated temporary password (ADR-0023).
 *
 * <p>Username only, no email and no phone: the first release signs everyone in with a school-issued
 * username and password (ADR-0003), and {@code AuthenticationService} only ever looks one up by
 * {@code IdentifierType.USERNAME}. Adding email or phone as a login identifier later is a new
 * request field and a new {@code CredentialVerifier}, not a change to this one (ADR-0003).
 */
public record CreateUserAccountRequest(
        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 100) String username,

        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 200) String displayName) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
