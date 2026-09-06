package in.chalkbase.platform.audit;

import in.chalkbase.platform.tenancy.TenantContext;
import in.chalkbase.platform.web.CurrentRequest;
import in.chalkbase.platform.web.RequestId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * How every module writes to the school's audit log (ADR-0018, FR-008).
 *
 * <p><strong>There are two entry points and they are not interchangeable. Read this before
 * merging them.</strong>
 *
 * <ul>
 *   <li>{@link #recordChange} joins the caller's transaction. If the change rolls back, its audit
 *       row goes with it — an audit log that records changes which did not happen cannot be
 *       reconciled against the data, which is worse than one with gaps.
 *   <li>{@link #recordSecurityEvent} runs in a transaction of its own and commits whatever happens
 *       to the caller's. A failed sign-in must be recorded <em>because</em> it failed.
 * </ul>
 *
 * <p>The two rules look inconsistent and are not. A data-change audit answers "what is the history
 * of this record", so it must match the record. A security audit answers "what did someone
 * attempt", so it must survive the attempt failing. One method with a flag would be one method that
 * gets the flag wrong on the day it matters.
 *
 * <p><strong>Field names are recorded. Values are not.</strong> {@link #recordChange} rejects
 * anything in {@code changedFields} that does not look like a field name, so ADR-0014 is mechanical
 * here rather than remembered. Storing before-and-after values would make this table a complete,
 * unencrypted, permanently retained second copy of every student record. Where a previous value
 * genuinely matters the domain carries it — money is append-only with reversals — and the audit log
 * is not the mechanism.
 *
 * <p>Never pass a password, a hash, a session id or a token to any method here. There is no
 * parameter that would take one, and that is deliberate.
 *
 * <p>The transaction annotations themselves live on {@link AuditEventWriter}, one proxy hop away,
 * for the reason set out there — a tenant has to be bound before the transaction opens, and a
 * self-invocation would bypass the proxy entirely.
 */
@Service
public class AuditService {

    /**
     * What may appear in {@code changed_fields}: a Java-ish field name, optionally dotted for a
     * nested one. This is the enforcement half of ADR-0014 — {@code "phone"} passes,
     * {@code "phone=9876543210"} does not, and neither does a name with a space, a comma or a
     * quote in it. You cannot smuggle a value past this without deliberately disguising it as an
     * identifier.
     */
    private static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*$");

    private static final int MAX_ACTOR_NAME = 200;
    private static final int MAX_ACTOR_ROLES = 400;
    private static final int MAX_ENTITY_TYPE = 60;
    private static final int MAX_ENTITY_ID = 100;
    private static final int MAX_USER_AGENT = 400;
    private static final int MAX_TRACE_ID = 64;

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventWriter writer;
    private final List<AuditActorResolver> actorResolvers;

    public AuditService(AuditEventWriter writer, List<AuditActorResolver> actorResolvers) {
        this.writer = writer;
        this.actorResolvers = List.copyOf(actorResolvers);
    }

    /**
     * Records a data change <strong>in the caller's transaction</strong> (ADR-0018 §3).
     *
     * <p>Call it from inside the service method that makes the change, after the change and before
     * the method returns. If that transaction rolls back, so does this row, and the log stays
     * reconcilable against the data.
     *
     * <p><strong>Do not "simplify" this into {@link #recordSecurityEvent}.</strong> The propagation
     * is the decision, not an implementation detail: a change audited in its own transaction would
     * survive its own rollback and leave the log claiming an edit the database never took.
     *
     * @param action what happened — {@link AuditAction#ENTITY_UPDATED}, or a verb this module owns
     * @param entityType the kind of thing changed, e.g. {@code STUDENT}
     * @param entityId its identifier
     * @param changedFields the NAMES of the fields that changed. Never a value: anything that is
     *     not a plain field name is rejected outright (ADR-0014)
     * @throws IllegalArgumentException if a "field name" is not one
     * @throws IllegalStateException if no tenant is bound, which means the change itself was not
     *     written to a school's schema either
     */
    public void recordChange(String action, String entityType, String entityId, Collection<String> changedFields) {
        String schema = TenantContext.currentSchema()
                .orElseThrow(() -> new IllegalStateException("recordChange(" + action
                        + ") with no tenant bound. A data change is always inside one school's schema;"
                        + " if this one was not, the change itself went to `public`."));

        AuditEvent event = build(
                action,
                AuditOutcome.SUCCESS,
                entityType,
                entityId,
                fieldNames(changedFields),
                currentActor(schema),
                null);
        writer.writeJoiningCaller(event);
    }

    /**
     * Records one bulk action — an import, a mass promotion — as a single event carrying how many
     * records it touched.
     *
     * <p>One row, not one per record. Six hundred {@code ENTITY_CREATED} rows would bury every
     * other thing that happened that day in the one log a principal reads to find out what happened
     * that day, and the individual records are recoverable from their own {@code created_at}
     * anyway.
     *
     * <p>{@code recordCount} is a property of the event and not a value of a field, which is why it
     * has a column of its own rather than being written into {@code changedFields}. Encoding it
     * there as {@code imported_600} would pass the field-name check and would be smuggling a value
     * past a rule built to stop exactly that.
     *
     * <p>Joins the caller's transaction, like {@link #recordChange} and for the same reason: if the
     * import rolls back, the log must not claim it happened.
     */
    public void recordBulkChange(
            String action, String entityType, String entityId, Collection<String> changedFields, int recordCount) {
        String schema = TenantContext.currentSchema()
                .orElseThrow(() -> new IllegalStateException("recordBulkChange(" + action
                        + ") with no tenant bound. A bulk change is always inside one school's schema."));
        if (recordCount < 0) {
            throw new IllegalArgumentException("A bulk action cannot have touched " + recordCount + " records");
        }

        AuditEvent event = build(
                action,
                AuditOutcome.SUCCESS,
                entityType,
                entityId,
                fieldNames(changedFields),
                currentActor(schema),
                recordCount);
        writer.writeJoiningCaller(event);
    }

    /**
     * Records a security event <strong>in a transaction of its own</strong> (ADR-0018 §4).
     *
     * <p>Sign-in, failed sign-in, lockout, permission denial, and an unmasking or export of
     * protected data. These are recorded whether or not the surrounding work succeeded, because
     * what they answer is "what did someone attempt" — and the interesting attempts are the ones
     * that did not work.
     *
     * <p><strong>Do not "simplify" this into {@link #recordChange}.</strong> Joining the caller's
     * transaction would mean a failed sign-in rolls its own record back, so the log would contain
     * only successful logins: an attacker's five hundred guesses would leave no trace at all, and
     * the one thing FR-008 exists for would be missing.
     *
     * <p>Never throws. An audit failure must not turn a 403 into a 500, or stop a sign-in that has
     * otherwise succeeded; it is logged at error instead. This is the one asymmetry with
     * {@link #recordChange}, which propagates because a change whose audit failed should not stand.
     *
     * <p>Nothing is recorded when there is no school to record it against — a sign-in attempt with
     * an unknown school code has no tenant, and ADR-0018 §5 says so explicitly. That is a
     * platform-level concern for later, not a hole in a school's audit trail.
     *
     * @param action {@link AuditAction#LOGIN_FAILED} and friends
     * @param outcome how it ended
     * @param entityType what {@code entityId} identifies, e.g. {@code USERNAME}
     * @param entityId the identifier attempted or acted on — an identifier, never a value
     */
    public void recordSecurityEvent(String action, AuditOutcome outcome, String entityType, String entityId) {
        recordSecurityEvent(action, outcome, entityType, entityId, null);
    }

    /**
     * As {@link #recordSecurityEvent(String, AuditOutcome, String, String)}, with the actor stated
     * rather than resolved.
     *
     * @param actor who acted, or null both for "resolve it from the security context" and for an
     *     event with genuinely no actor — an unauthenticated failed sign-in has no actor id, and
     *     recording the attempted username in {@code entityId} is what identifies it instead
     */
    public void recordSecurityEvent(
            String action, AuditOutcome outcome, String entityType, String entityId, AuditActor actor) {
        try {
            AuditActor resolved = actor != null ? actor : currentActor(null);
            String schema =
                    TenantContext.currentSchema().orElseGet(() -> resolved == null ? null : resolved.tenantSchema());
            if (schema == null) {
                // ADR-0018 §5: a school is what an audit row belongs to. With no school there is
                // nowhere to write, and guessing `public` would put one school's security events
                // in the registry schema.
                log.warn("Not recording {}: no school is bound and the actor names none", action);
                return;
            }

            AuditEvent event = build(action, outcome, entityType, entityId, null, resolved, null);
            inSchema(schema, () -> {
                writer.writeInOwnTransaction(event);
                return null;
            });
        } catch (RuntimeException ex) {
            log.error("Failed to record the security event {} (outcome {})", action, outcome, ex);
        }
    }

    /**
     * Records the 403 the caller is about to be sent.
     *
     * <p>Exists so a denial is recorded <strong>once, in one shape</strong>, from the two places a
     * 403 is actually produced: {@code platform.error.SecurityErrorResponder} for a denial raised
     * inside the security filter chain (a URL rule, a missing CSRF token), and
     * {@code platform.error.GlobalExceptionHandler} for one raised by a method-level
     * {@code @PreAuthorize} once the request has reached a controller. Exactly one of those handles
     * any given request — whichever writes the response is the one that ran — so calling this from
     * both gives one row per denial rather than one per filter pass.
     *
     * <p>Auditing inside an authorization manager or a filter instead would count wrong, because
     * the chain evaluates authorization more than once for a single request.
     */
    public void recordPermissionDenied() {
        recordSecurityEvent(AuditAction.PERMISSION_DENIED, AuditOutcome.DENIED, "ENDPOINT", CurrentRequest.endpoint());
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    /**
     * Asks each registered resolver in turn. There is one today; a second authentication mechanism
     * is a second bean and no change here.
     *
     * @param fallbackSchema used when the resolver knows who is acting but the caller already knows
     *     the school — the resolver's answer wins only if it has one
     */
    private AuditActor currentActor(String fallbackSchema) {
        for (AuditActorResolver resolver : actorResolvers) {
            Optional<AuditActor> actor = resolver.currentActor();
            if (actor.isPresent()) {
                AuditActor found = actor.get();
                String schema = found.tenantSchema() != null ? found.tenantSchema() : fallbackSchema;
                return new AuditActor(
                        found.id(),
                        truncate(found.name(), MAX_ACTOR_NAME),
                        truncate(found.roles(), MAX_ACTOR_ROLES),
                        schema);
            }
        }
        return null;
    }

    private AuditEvent build(
            String action,
            AuditOutcome outcome,
            String entityType,
            String entityId,
            String changedFields,
            AuditActor actor,
            Integer recordCount) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("An audit event needs an action");
        }
        if (action.length() > AuditAction.MAX_LENGTH) {
            throw new IllegalArgumentException("Audit action '" + action + "' is longer than the "
                    + AuditAction.MAX_LENGTH + " characters the column holds");
        }
        return new AuditEvent(
                Instant.now(),
                actor,
                action,
                outcome == null ? AuditOutcome.SUCCESS : outcome,
                truncate(entityType, MAX_ENTITY_TYPE),
                truncate(entityId, MAX_ENTITY_ID),
                changedFields,
                CurrentRequest.ipAddress(),
                truncate(CurrentRequest.userAgent(), MAX_USER_AGENT),
                truncate(RequestId.current(), MAX_TRACE_ID),
                recordCount);
    }

    /**
     * Sorted, de-duplicated, comma-separated field NAMES — and only names.
     *
     * <p>The rejection is the point. ADR-0014 says Restricted and Confidential data is never
     * logged, and the easiest way to break that here is to write {@code "phone -> 98765xxxxx"} into
     * a field that looks like it is for describing a change. This makes that a build-time-obvious
     * failure at the call site rather than a discovery someone makes in a school's database.
     */
    private static String fieldNames(Collection<String> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) {
            return null;
        }
        List<String> rejected = changedFields.stream()
                .filter(field -> field == null || !FIELD_NAME.matcher(field).matches())
                .toList();
        if (!rejected.isEmpty()) {
            throw new IllegalArgumentException("changed_fields holds field NAMES, never values (ADR-0014). Rejected: "
                    + rejected + ". If you need the previous value, the audit log is not the mechanism —"
                    + " version it in its own model, with its own classification and retention.");
        }
        return changedFields.stream().distinct().sorted().collect(Collectors.joining(","));
    }

    /** Binds the school only when nothing is bound, so a caller already inside a tenant is left alone. */
    private static <T> T inSchema(String schema, Callable<T> work) {
        if (schema.equals(TenantContext.currentSchemaOrPlatform())
                && TenantContext.currentSchema().isPresent()) {
            return call(work);
        }
        try {
            return TenantContext.callWith(schema, work);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Audit write failed for schema " + schema, ex);
        }
    }

    private static <T> T call(Callable<T> work) {
        try {
            return work.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
