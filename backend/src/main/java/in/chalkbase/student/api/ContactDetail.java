package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.StudentContact;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A student's own address, phone and email (FR-028).
 *
 * <p>Confidential under ADR-0014, like the rest of the student record. Nothing here is encrypted or
 * masked — see {@code StudentMedical} and {@code StudentCompliance} for what is.
 */
public record ContactDetail(
        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String address,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String phone,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String email) {

    public static ContactDetail of(StudentContact contact) {
        return new ContactDetail(contact.getAddress(), contact.getPhone(), contact.getEmail());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
