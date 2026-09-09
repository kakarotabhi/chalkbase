package in.chalkbase.communication.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.Size;

/** Records that a recipient's family acknowledged a circular, on their behalf. */
public record AcknowledgeRecipientRequest(
        @Classification(Tier.CONFIDENTIAL) @Size(max = 500) String note) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
