package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.Guardian;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One row of the guardian directory.
 *
 * <p><strong>{@link #linkedStudentCount()} is the reason this list exists at all.</strong> The
 * directory is what the office searches before typing a father in for a second child (ADR-0020 §5),
 * and the count is what tells the clerk that the "Suresh Pillai" they have found is already
 * somebody's parent rather than a stranger with the same name. Without it, a search for a common
 * name returns four indistinguishable rows and the safest thing a clerk can do is create a fifth —
 * which is the duplication the shared-guardian model exists to prevent.
 *
 * <p>Whose children they are is deliberately not here. A guardian directory that listed each
 * person's students would let anyone holding {@code student:guardian:read} enumerate the school's
 * families from a screen meant for finding one person; the count answers the question the screen
 * actually asks without answering that one.
 *
 * <p>Every field but the id and the count is Confidential under ADR-0014.
 */
public record GuardianSummary(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.CONFIDENTIAL) String fullName,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String phone,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String email,

        @Schema(nullable = true) @Classification(Tier.CONFIDENTIAL)
        String occupation,

        @Classification(Tier.INTERNAL) long linkedStudentCount) {

    public static GuardianSummary of(Guardian guardian, long linkedStudentCount) {
        return new GuardianSummary(
                guardian.getId(),
                guardian.getFullName(),
                guardian.getPhone(),
                guardian.getEmail(),
                guardian.getOccupation(),
                linkedStudentCount);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
