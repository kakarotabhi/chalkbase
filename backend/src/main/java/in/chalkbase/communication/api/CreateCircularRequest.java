package in.chalkbase.communication.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Composes a circular in {@code DRAFT}, with the targets it will publish to.
 *
 * <p>Targets are given up front rather than added one at a time against an already-created draft:
 * this build ships no endpoint to edit a draft's targets after creation, so a circular is composed
 * whole, the same "everything in one call" shape {@code MarkAttendanceRequest} uses for a section's
 * roster. A school that wants to change the audience discards the draft and composes another —
 * cheap while nothing has been published yet, and this is the only state a target can be edited in.
 */
public record CreateCircularRequest(
        @Classification(Tier.INTERNAL) @NotBlank @Size(max = 200) String title,

        @Classification(Tier.INTERNAL) @NotBlank String body,
        @Classification(Tier.INTERNAL) boolean requiresAcknowledgement,
        @Classification(Tier.INTERNAL) @NotEmpty List<@Valid CircularTargetRequest> targets) {

    public CreateCircularRequest {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
