package in.chalkbase.communication.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;

/**
 * How many actively enrolled students one candidate target would reach, before a circular is
 * composed or published.
 *
 * <p>A headcount, the same tier {@code student.api.SectionEnrolmentCount} uses and for the same
 * reason: it identifies nobody. Gated on {@code communication:circular:read} — this module's own
 * permission, never {@code academics:class:read} or {@code student:student:read} — which is the
 * whole point named in the module's package doc: a role that may compose circulars sees this
 * count without needing any other module's read permission, and a role that holds some other
 * module's permission gains nothing here from it.
 */
public record TargetPreviewResponse(
        @Classification(Tier.INTERNAL) long count) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
