/**
 * Communication: circulars and notices, targeted by class and section (FR-097–FR-104, Phase 2).
 *
 * <p>The slice this build ships is deliberately narrow. A circular is composed, targeted at one or
 * more classes or sections, published, and then read as a list of per-student recipients with a
 * delivery timestamp and an optional acknowledgement — never a broadcast to "everyone", which is
 * the trap {@code docs/requirements/08-phase-2-scope.md} names for this module explicitly: a
 * mailing list with a send button is not "circulars are done".
 *
 * <p><strong>Deferred, on purpose:</strong>
 *
 * <ul>
 *   <li>SMS and WhatsApp channels (FR-098) — blocked on TRAI DLT registration (ADR-0013), which has
 *       not started.
 *   <li>Targeting by fee status, attendance status, transport route, hostel, house or club
 *       (FR-100) — the first two are privacy-sensitive in a way class/section targeting is not (see
 *       the module's own read permission, below), and none of the underlying data exists yet for
 *       the rest.
 *   <li>Multilingual templates (FR-099) — P1.
 *   <li>PTM scheduling (FR-102) and helpdesk/ticketing (FR-103) — not on the Phase 2 roadmap at
 *       all.
 * </ul>
 *
 * <h2>What a recipient record is, today</h2>
 *
 * <p>{@code circular_recipient} carries a plain {@code student_id}, not a guardian account —
 * {@code ScopeType.WARD} is deliberately unassignable and no guardian has an account merely
 * because their child is enrolled (ADR-0017), so "delivered to a parent's inbox" is not a thing
 * this build can mean yet. What is real today: the row exists the moment a circular is published
 * (there is no queue to sit in — see below), and it is keyed by the one identifier that survives a
 * guardian's account being created later. When the parent portal ships, "my circulars" becomes
 * {@code recipients where student_id in (wards of the signed-in guardian)} — an additive read
 * path, resolved through {@code student.api.StudentLookup}'s guardian-of relationship, requiring
 * no migration and no rewrite of history. Until then, the recipient list is a staff-facing screen:
 * seeing who a circular reached, and recording that a family acknowledged it — by phone, in
 * writing, or in person — is legitimate school-office work in its own right, not a stand-in for a
 * parent login.
 *
 * <h2>Why there is no channel port here</h2>
 *
 * <p>ADR-0013's {@code NotificationChannel} port exists for delivery that happens outside this
 * system and can fail independently of it — an email that bounces, an SMS the carrier drops —
 * which is why it is queued, asynchronous, and carries retry and failure states. An in-app
 * circular is not delivered anywhere: it is a row this system already holds, read directly by a
 * screen inside the same transaction boundary that created it. There is nothing to queue and
 * nothing that fails independently, so a port here would have exactly one implementation with no
 * failure mode to justify the abstraction — the "stub pretending to be email" this module was
 * explicitly told not to build. {@code circular_recipient} is written directly by
 * {@code CircularPublishingService} and read directly by a screen. When email or push adapters
 * land, they consume {@code circular_recipient} rows as their own send queue's source — a new call
 * made *from* this module *to* {@code platform}'s port — which is forward-compatible with the
 * columns this table already has ({@code delivered_at}, {@code viewed_at}) and needs no reshaping.
 *
 * <h2>The privacy trap this module was warned about</h2>
 *
 * <p>Audience targeting is itself Confidential-adjacent under ADR-0014, even restricted to class
 * and section: knowing which children a circular reached is close kin to knowing who is enrolled
 * where. {@code CommunicationPermissions} defines its own {@code communication:circular:read},
 * enforced on the target-preview endpoint independently of {@code academics:class:read} or any
 * permission {@code fee} or {@code attendance} defines — a school that wants a front-office role to
 * compose circulars without also handing it the fee module's defaulter list gets that separation
 * for free, because the two are never the same permission.
 *
 * <p>Reaches {@code academics} and {@code student} only through their named interfaces,
 * {@code academics.api.AcademicsLookup} and {@code student.api.StudentLookup}. Every table this
 * module owns is per-tenant and carries no {@code school_id}: the PostgreSQL schema is the tenant
 * boundary (ADR-0011).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Communication")
package in.chalkbase.communication;
