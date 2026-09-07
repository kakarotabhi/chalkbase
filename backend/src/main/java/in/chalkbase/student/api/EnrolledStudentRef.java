package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One student on a section's roster, as another module sees it.
 *
 * <p>Added for the attendance module, which is the first caller of {@link StudentLookup} that
 * needs more than a count — a marking screen has to show whom it is marking. Confidential under
 * ADR-0014, the same tier every other name-carrying row in this module's API is: this is not a
 * headcount like {@link SectionEnrolmentCount}, it identifies a specific child.
 *
 * <p>Active enrolments only, the same scoping every {@link StudentLookup} method uses: a student
 * who has left the section, or the school, is not on a roster anyone is marking against today.
 *
 * @param rollNumber null until the class list settles — assigned after admission, often after roll
 *     call already matters, so a caller cannot assume it is there.
 */
public record EnrolledStudentRef(
        @Classification(Tier.INTERNAL) UUID studentId,
        @Classification(Tier.CONFIDENTIAL) String admissionNumber,
        @Classification(Tier.CONFIDENTIAL) String fullName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String rollNumber) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
