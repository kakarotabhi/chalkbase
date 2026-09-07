package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The landing screen's tiles, cut down to what the caller may see (ADR-0008 applied to data, not
 * only to menus).
 *
 * <p><strong>Every field is independently nullable, and that is the authorization model, not an
 * accident of the shape.</strong> Each tile is gated on the read permission the module that owns
 * its data already defined — {@code academics:session:read}, {@code student:student:read},
 * {@code student:guardian:read}, {@code platform:audit:read} — and {@link DashboardService} decides
 * server-side which of the four the caller holds before building this record. A tile the caller may
 * not see is never computed and never sent, the same "omitted, not zero, not null-standing-in"
 * convention {@link LinkageGapsTile} applies at the field level. There is deliberately no fifth
 * field naming which permissions were checked: the screen renders what it is given and asks
 * nothing about why a tile is missing, the same way {@code NavigationItem} never explains why an
 * item was filtered.
 *
 * <p>Kept to four tiles on purpose (see the PR this shipped in for what was cut and why): a tile
 * nobody looking at this screen can act on is decoration, and Phase 1 has exactly five modules
 * with real data to summarise honestly.
 */
public record DashboardResponse(
        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        SessionTile session,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        StudentsTile students,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        LinkageGapsTile linkageGaps,

        @Schema(nullable = true) @Classification(Tier.INTERNAL)
        RecentAuditTile recentAudit) {

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
