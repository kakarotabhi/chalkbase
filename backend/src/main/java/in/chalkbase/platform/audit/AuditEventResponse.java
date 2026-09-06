package in.chalkbase.platform.audit;

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
        UUID id,
        Instant occurredAt,
        UUID actorId,
        String actorName,
        List<String> actorRoles,
        String action,
        AuditOutcome outcome,
        String entityType,
        String entityId,
        List<String> changedFields,
        String ipAddress,
        String userAgent,
        String traceId) {

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
                event.getTraceId());
    }

    private static List<String> split(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return List.of(commaSeparated.split(","));
    }
}
