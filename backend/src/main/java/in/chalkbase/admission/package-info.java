/**
 * Admissions: enquiry management (FR-016, FR-017), the front of Phase 0 decision §5's pipeline.
 *
 * <p>This lane builds one slice of a larger module the roadmap gives four features:
 * {@code Enquiry → Application → Document verification → Screening outcome → Approval →
 * Admission fee → Student record created}. Only the first box — the enquiry itself, its status, its
 * assigned counsellor and its follow-up history — has a table here. The online admission form, the
 * application workflow beyond an enquiry's own four statuses, and student conversion are later
 * lanes; see {@code docs/requirements/08-phase-2-scope.md} §"Admissions" for the boundary and why
 * it is drawn there.
 *
 * <p>The trap that section names, and the reason this module is not just a capture form: an
 * enquiry with nobody assigned and no due-date list of pending follow-ups is a mailbox. Every
 * {@code enquiry} row is created with a required counsellor and a {@code next_follow_up_date} that
 * defaults to the day it is captured, so a fresh enquiry is due for a first follow-up the moment it
 * exists rather than after somebody remembers to schedule one. See {@code Enquiry}'s own Javadoc.
 *
 * <p>Every table this module owns is per-tenant and carries no {@code school_id}: the PostgreSQL
 * schema is the tenant boundary (ADR-0011). Reaches {@code academics} and {@code identity} only
 * through their named interfaces, {@code academics.api.AcademicsLookup} and the new
 * {@code identity.api.IdentityLookup} — the first caller of the latter, added for this module (see
 * its own Javadoc for what changed and why).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Admission")
package in.chalkbase.admission;
