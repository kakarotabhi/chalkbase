package in.chalkbase.admission.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Assigns or reassigns the counsellor responsible for one enquiry's follow-up. */
public record AssignCounsellorRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID counsellorId) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
