package in.chalkbase.platform.audit;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One audit row as the API returns it.
 *
 * <p>A record at the boundary, never the entity: {@link AuditEvent} is a JPA-managed row in one
 * school's schema and exposing it would put its lifecycle and its lazy state on the wire.
 *
 * <p>{@code changedFields} is a list here and a comma-separated string in the column, because a
 * client wants to render "name, section" and a database wants one value. It contains field NAMES
 * only — see {@link AuditService} and ADR-0014. There is no before/after pair to return, and there
 * never will be.
 *
 * <p>{@code actorName} and {@code actorRoles} read as they did when the action happened, not as
 * they read now. That is the whole reason they are snapshots.
 */
public record AuditEventResponse(
        @Classification(Tier.INTERNAL) UUID id,
        @Classification(Tier.INTERNAL) Instant occurredAt,
        @Classification(Tier.INTERNAL) UUID actorId,
        @Classification(Tier.CONFIDENTIAL) String actorName,
        @Classification(Tier.INTERNAL) List<String> actorRoles,
        @Classification(Tier.INTERNAL) String action,
        @Classification(Tier.INTERNAL) AuditOutcome outcome,
        @Classification(Tier.INTERNAL) String entityType,
        @Classification(Tier.INTERNAL) String entityId,
        @Classification(Tier.INTERNAL) List<String> changedFields,
        @Classification(Tier.CONFIDENTIAL) String ipAddress,
        @Classification(Tier.INTERNAL) String userAgent,
        @Classification(Tier.INTERNAL) String traceId,
        /**
         * How many records a bulk action touched, or null when it touched one.
         *
         * <p>Absent on almost every row, and that is the point: an import of six hundred students
         * is one event, and this is the number that makes it legible as one.
         */
        @Classification(Tier.INTERNAL) Integer recordCount) {

    static AuditEventResponse of(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getActorId(),
                event.getActorName(),
                split(event.getActorRoles()),
                event.getAction(),
                event.getOutcome(),
                event.getEntityType(),
                event.getEntityId(),
                split(event.getChangedFields()),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getTraceId(),
                event.getRecordCount());
    }

    private static List<String> split(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return List.of(commaSeparated.split(","));
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
