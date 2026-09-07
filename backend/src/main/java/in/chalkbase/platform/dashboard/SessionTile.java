package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The current academic session, and whether the school has set one at all — the first thing a
 * principal opening Chalkbase in the morning wants to know, because every other tile that counts
 * students depends on there being a year to enrol them into.
 *
 * <p>Built by {@code academics.application}'s {@link AcademicsDashboardContributor} implementation
 * from its own repository, never from {@code academics.api.AcademicSessionRef} — this record lives
 * in the shared kernel, which must not import a feature module (the same reason
 * {@code platform.audit.AuditActorResolver} exists rather than the audit log reaching into
 * identity's principal type directly). The plain canonical constructor is how that module builds
 * one.
 *
 * @param set false when no session has been marked current yet (ADR-0019). Every field below is
 *     then absent: there is nothing to name, and sending zeros or an empty string would read as a
 *     year with no students rather than as no year at all.
 */
public record SessionTile(
        @Classification(Tier.INTERNAL) boolean set,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        UUID sessionId,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        String name,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        LocalDate startsOn) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
