package in.chalkbase.school.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Public read model of a school. Never expose the JPA entity across a module or HTTP boundary. */
public record SchoolResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.PUBLIC) String code,
        @Classification(Tier.PUBLIC) String name,
        @Classification(Tier.INTERNAL) String schemaName,
        @Classification(Tier.PUBLIC) Board board,

        @Schema(nullable = true) @Classification(Tier.PUBLIC)
        String city,

        @Schema(nullable = true) @Classification(Tier.PUBLIC)
        String state,

        @Classification(Tier.INTERNAL) boolean active) {

    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getCode(),
                school.getName(),
                school.getSchemaName(),
                school.getBoard(),
                school.getCity(),
                school.getState(),
                school.isActive());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
