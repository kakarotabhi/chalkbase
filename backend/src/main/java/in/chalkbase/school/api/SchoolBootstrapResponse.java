package in.chalkbase.school.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * What {@code POST /api/schools/bootstrap} hands back, once (ADR-0024): the school, and the one
 * sign-in that can now be used to configure it.
 *
 * <p>{@code temporaryPassword} is shown exactly once, the same rule {@link
 * in.chalkbase.identity.api.NewUserAccountResponse} documents: nothing stores it, there is no
 * endpoint that can retrieve it again, and the person who ran the bootstrap is responsible for
 * getting it to whoever will actually sign in — a deployment secret handed over the way a school
 * office already hands over a slip of paper. {@code adminMustChangePassword} is always {@code true}
 * — recorded here rather than assumed, so a client does not have to know that by heart.
 */
public record SchoolBootstrapResponse(
        @Classification(Tier.INTERNAL) SchoolResponse school,
        @Classification(Tier.INTERNAL) UUID adminAccountId,
        @Classification(Tier.CONFIDENTIAL) String adminUsername,
        @Classification(Tier.CONFIDENTIAL) String adminTemporaryPassword,
        @Classification(Tier.INTERNAL) boolean adminMustChangePassword) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
