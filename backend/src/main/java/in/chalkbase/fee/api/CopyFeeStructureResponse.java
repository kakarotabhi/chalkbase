package in.chalkbase.fee.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.List;

/**
 * What a {@link CopyFeeStructureRequest} actually did — never silent, per ADR-0033's own argument
 * for making a copy an explicit, reported action rather than something that happens off-screen.
 *
 * @param skippedClassNames classes that already had a structure in the destination session, and so
 *     were left untouched. Named rather than counted: the screen tells the school exactly which
 *     classes it still needs to look at.
 * @param copied the newly written structures, one version-1 row per class actually copied.
 */
public record CopyFeeStructureResponse(
        @Classification(Tier.INTERNAL) List<String> skippedClassNames,
        @Classification(Tier.INTERNAL) List<FeeStructureResponse> copied) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
