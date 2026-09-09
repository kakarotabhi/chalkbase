package in.chalkbase.fee.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * The complete new fee structure for one class in one session — a whole replacement, never a
 * patch.
 *
 * <p>The academic session and the class both come from the URL path
 * ({@code PUT /api/fees/structures/{sessionId}/{classId}}), not from this body: a save is always
 * about one class's one session, and naming them twice would let a mismatched path and body
 * disagree about which one is being changed.
 *
 * <p><strong>The whole item list, every time</strong> — the same shape
 * {@code ReorderSchoolClassesRequest} uses for the ladder. There is no per-item PATCH: this record
 * becomes a brand new {@code fee_structure} version with the exact items and installments given,
 * and whatever the previous version held that is not repeated here is simply not carried forward.
 * A screen that means "change one amount" resubmits every item, pre-filled from what it loaded.
 */
public record SaveFeeStructureRequest(
        @Classification(Tier.INTERNAL) @NotEmpty @Valid List<FeeStructureItemRequest> items) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
