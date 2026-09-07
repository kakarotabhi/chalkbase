package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * A student's address, phone and email, corrected. One shape, whether the section is being entered
 * for the first time or edited — as {@code SaveStudentRequest} is.
 *
 * <p>Every field optional: a fresh admission may have an address on file and no phone of their own,
 * and requiring one would be answered with a guess.
 */
public record SaveContactRequest(
        @Classification(Tier.CONFIDENTIAL) @Size(max = 2000) String address,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 20) String phone,

        @Classification(Tier.CONFIDENTIAL) @Email @Size(max = 320) String email) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
