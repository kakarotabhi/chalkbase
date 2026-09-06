package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.student.domain.GuardianRelation;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * An <strong>existing</strong> guardian attached to a child. The child is in the path.
 *
 * <p>It takes a {@code guardianId} and not a name, and that is the decision ADR-0020 §5 is about.
 * A body that carried the person's details would create a new row every time, so a father of four
 * would end up in the table four times — and the day the school corrects one of them, the other
 * three children keep a number that no longer answers, with nothing in the system aware they
 * disagree. The office searches {@code GET /api/guardians?q=} first and attaches what it finds;
 * creating a person is {@code POST /api/guardians}, which is a separate act.
 *
 * @param primary whether the school should ring this person first. Setting it clears whichever link
 *     held it before, in the same transaction — {@code uq_student_guardian_one_primary} refuses
 *     anything else.
 */
public record LinkGuardianRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID guardianId,
        @Classification(Tier.INTERNAL) @NotNull GuardianRelation relation,
        @Classification(Tier.INTERNAL) @NotNull Boolean primary) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
