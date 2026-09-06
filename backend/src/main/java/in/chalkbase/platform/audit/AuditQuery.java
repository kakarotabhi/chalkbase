package in.chalkbase.platform.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * What to narrow the audit log by. Every field is optional; all of them are ANDed.
 *
 * <p>These three are the questions an audit log is actually asked — "what did this person do",
 * "when did this kind of thing happen", "what happened in this window" — and they are the three the
 * migration's indexes are built for. Anything richer belongs in an export, not in a filter.
 *
 * <p><strong>There is deliberately no free-text search.</strong> Matching on
 * {@code entity_id} substrings would turn the audit log into a way to discover which children are
 * enrolled, for anyone holding a read permission granted for an inspection.
 *
 * @param actorId one person's actions
 * @param action one verb, e.g. {@code LOGIN_FAILED}
 * @param from inclusive lower bound on {@code occurredAt}
 * @param to exclusive upper bound on {@code occurredAt}, so consecutive ranges do not overlap
 */
public record AuditQuery(UUID actorId, String action, Instant from, Instant to) {

    /** Everything, unnarrowed. */
    public static AuditQuery all() {
        return new AuditQuery(null, null, null, null);
    }
}
