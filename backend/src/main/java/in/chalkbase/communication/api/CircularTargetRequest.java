package in.chalkbase.communication.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One class or section to target, within a {@link CreateCircularRequest}.
 *
 * <p>No {@code @Schema(nullable = true)} on {@code sectionId}: that annotation is for a
 * <strong>response</strong> field, where {@code OpenApiConfig}'s customizer strips the marker back
 * out before export. On a request field it survives into {@code contracts/openapi.json} as OpenAPI
 * 3.1's {@code "type": ["string", "null"]}, which {@code OpenApiContractTests
 * #nullableMeansAbsentRatherThanNull} refuses outright. A request field's optionality is already
 * expressed by the absence of {@code @NotNull}, which springdoc reads directly — the same
 * convention {@code SaveStudentRequest#admittedOn} and {@code CreateSchoolRequest#city}/{@code
 * state} already follow.
 *
 * @param sectionId null to mean "every active section of this class". Given, means one specific
 *     section, which must belong to {@code classId} ({@link
 *     in.chalkbase.communication.domain.CommunicationErrorCode#INVALID_TARGET} otherwise).
 */
public record CircularTargetRequest(
        @Classification(Tier.INTERNAL) @NotNull UUID classId,
        @Classification(Tier.INTERNAL) UUID sectionId) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
