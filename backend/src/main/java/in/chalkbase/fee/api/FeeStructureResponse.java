package in.chalkbase.fee.api;

import in.chalkbase.fee.domain.FeeStructure;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The live version of one class's fee structure for one academic session (ADR-0012 rule 6,
 * ADR-0033).
 *
 * <p>Carries the session's and class's names inline, resolved through
 * {@code academics.api.AcademicsLookup} — the same reason {@link FeeStructureItemResponse} carries
 * its fee head's name: nothing that renders this needs a second call to say what it is looking at.
 *
 * @param version this session's edit number for this class, starting at 1. A version other than
 *     the highest for its (session, class) is never returned by this module's read endpoints —
 *     see {@code FeeStructureService} for why a superseded version is not exposed at all in this
 *     lane.
 */
public record FeeStructureResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) UUID academicSessionId,
        @Classification(Tier.INTERNAL) String academicSessionName,
        @Classification(Tier.INTERNAL) UUID schoolClassId,
        @Classification(Tier.INTERNAL) String schoolClassName,
        @Classification(Tier.INTERNAL) int version,
        @Classification(Tier.INTERNAL) Instant createdAt,
        @Classification(Tier.INTERNAL) List<FeeStructureItemResponse> items) {

    public static FeeStructureResponse of(FeeStructure structure, String sessionName, String className) {
        return new FeeStructureResponse(
                structure.getId(),
                structure.getAcademicSessionId(),
                sessionName,
                structure.getSchoolClassId(),
                className,
                structure.getVersion(),
                structure.getCreatedAt(),
                structure.getItems().stream().map(FeeStructureItemResponse::of).toList());
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
