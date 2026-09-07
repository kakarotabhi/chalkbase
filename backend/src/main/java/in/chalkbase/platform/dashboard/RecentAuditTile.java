package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.audit.AuditEventResponse;
import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.List;

/**
 * The most recent audit activity, for someone who holds {@code platform:audit:read} and wants to
 * know what happened without opening the full log.
 *
 * <p>Reuses {@link AuditEventResponse} rather than a dashboard-shaped copy of it: it is already the
 * correctly classified boundary DTO for one audit row, {@code platform.audit} and
 * {@code platform.dashboard} sit in the same shared-kernel module, and a second shape here would be
 * a second place for ADR-0018's "field names, never values" rule to be gotten wrong.
 */
public record RecentAuditTile(@Classification(Tier.INTERNAL) List<AuditEventResponse> events) {

    public RecentAuditTile {
        events = events == null ? List.of() : List.copyOf(events);
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
