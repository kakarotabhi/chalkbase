package in.chalkbase.identity.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The current password is required even though the caller is already authenticated: it is what
 * stops a walked-away session being turned into a permanent takeover.
 *
 * <p>The strength rules are not expressed as a {@code @Pattern} here on purpose. They live in
 * {@code PasswordPolicy} so the failure comes back as {@code AUTH_007} with a sentence a parent can
 * act on, rather than as a generic field validation error quoting a regular expression.
 */
public record ChangePasswordRequest(
        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 200) String currentPassword,

        @Classification(Tier.CONFIDENTIAL) @NotBlank @Size(max = 200) String newPassword) {

    /** Neither password may reach a log line or an error message; both are Confidential. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
