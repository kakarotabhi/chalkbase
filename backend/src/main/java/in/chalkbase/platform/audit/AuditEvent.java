package in.chalkbase.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One audited action, in one school's schema (ADR-0018, FR-008).
 *
 * <p>Unqualified and with no {@code school_id} column: this lives in the school's own schema and is
 * reached through {@code search_path} (ADR-0011). The audit log is the school's record of itself
 * and belongs to the school's data, including for export and erasure.
 *
 * <p><strong>There are no setters and no update path.</strong> Append-only is not a convention here
 * — the class has nothing that could change a row, and there is no endpoint that could ask it to.
 * A retention purge is a scheduled platform job, not an API.
 *
 * <p><strong>{@code changedFields} holds field NAMES. Never values.</strong> Recording values would
 * make this table a complete, unencrypted, permanently retained second copy of every student
 * record — the largest concentration of children's data in the system, and the one nobody thinks of
 * as a database (ADR-0014). {@link AuditService} refuses anything that does not look like a field
 * name, so the rule is mechanical rather than remembered.
 *
 * <p>{@code actorName} and {@code actorRoles} are snapshots and deliberately not foreign keys; see
 * {@link AuditActor}.
 *
 * <p>Never returned over HTTP. {@code AuditEventResponse} is the boundary.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /**
     * Set here rather than left to the column default, so the caller and the row agree on the
     * instant even when the insert is batched or deferred to commit.
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    /** Null when nobody was authenticated — a failed sign-in has no actor, and that is the point. */
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    /** Comma-separated role codes, sorted. A snapshot, so a later role change cannot rewrite it. */
    @Column(name = "actor_roles", length = 400)
    private String actorRoles;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    /** Field names only, comma-separated and sorted. See the class javadoc and ADR-0014. */
    @Column(name = "changed_fields", columnDefinition = "text")
    private String changedFields;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    /** Personal data under the DPDP Act. Inherits the audit log's retention; never kept forever. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    /** The same id the ADR-0007 envelope returns, so a quoted trace id leads straight to here. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected AuditEvent() {
        // for JPA
    }

    AuditEvent(
            Instant occurredAt,
            AuditActor actor,
            String action,
            AuditOutcome outcome,
            String entityType,
            String entityId,
            String changedFields,
            String ipAddress,
            String userAgent,
            String traceId) {
        this.occurredAt = occurredAt;
        this.actorId = actor == null ? null : actor.id();
        this.actorName = actor == null ? null : actor.name();
        this.actorRoles = actor == null ? null : actor.roles();
        this.action = action;
        this.outcome = outcome;
        this.entityType = entityType;
        this.entityId = entityId;
        this.changedFields = changedFields;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.traceId = traceId;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorRoles() {
        return actorRoles;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getChangedFields() {
        return changedFields;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getTraceId() {
        return traceId;
    }
}
