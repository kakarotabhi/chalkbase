package in.chalkbase.platform.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two transaction boundaries of the audit log, and nothing else (ADR-0018 §3 and §4).
 *
 * <p><strong>Why this is a separate bean from {@link AuditService}.</strong> Hibernate chooses the
 * tenant when it opens a session at the start of a transaction, so a schema has to be bound
 * <em>before</em> the transactional method is entered — the same reason identity's transaction
 * boundaries sit in {@code UserAccountService} rather than on {@code AuthenticationService}. A
 * permission denial is produced inside the security filter chain, after the filter that binds the
 * tenant has already unbound it, so {@link AuditService} must bind one and then cross a proxy
 * boundary. Annotating {@code AuditService}'s own methods and having them call each other would not
 * work: a self-invocation never passes through the proxy, and the annotation would silently do
 * nothing.
 *
 * <p>So the reasoning lives on {@link AuditService}, where the callers read it, and the two
 * {@code @Transactional} declarations live here, one proxy hop away. Do not merge them.
 */
@Component
public class AuditEventWriter {

    private final AuditEventRepository events;

    public AuditEventWriter(AuditEventRepository events) {
        this.events = events;
    }

    /**
     * Writes inside whatever transaction the caller already has (ADR-0018 §3).
     *
     * <p>If that transaction rolls back, this row goes with it. An audit log that records changes
     * which did not happen is worse than one with gaps, because it cannot be reconciled against the
     * data it claims to describe.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void writeJoiningCaller(AuditEvent event) {
        events.save(event);
    }

    /**
     * Writes in a transaction of its own, committed whatever happens to the caller's (ADR-0018 §4).
     *
     * <p><strong>A failed sign-in must be recorded precisely because it failed.</strong> The
     * surrounding work throwing is not a reason to forget the attempt — it is the reason there is
     * an attempt worth remembering. Same reasoning that already puts the failed-attempt counter in
     * its own transaction in identity: recording it inside the transaction that then throws would
     * roll the counter back, and the lockout would never trigger.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInOwnTransaction(AuditEvent event) {
        events.save(event);
    }
}
