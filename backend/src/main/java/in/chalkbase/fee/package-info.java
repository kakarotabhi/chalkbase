/**
 * Fee structure: fee heads, concession types, and a session-scoped fee structure per class
 * (ADR-0012, ADR-0033, 08-phase-2-scope.md §"Fees").
 *
 * <p><strong>Scope of this lane.</strong> FR-075 (heads, groups, installments, due dates, waivers),
 * FR-076 (assign by class — see below for what is deferred), part of FR-077 (the six fee
 * schedules, as {@code InstallmentFrequency} on a structure item) and half of FR-078 (defining a
 * concession <em>type</em>). Not built: {@code fee_demand}, {@code fee_charge},
 * {@code fee_ledger_entry}, payments, receipts, dues, or applying a concession to any student's
 * actual charge — those are separate roadmap lines (fee demand; online/offline collection;
 * receipts; dues and reminders) with their own traps, per the scope document, and every one of
 * them hangs off {@code fee_demand}, which does not exist yet.
 *
 * <p><strong>Why defining a concession type is here and granting one is not.</strong> ADR-0012's
 * own diagram puts a concession on {@code fee_ledger_entry}, which references a {@code fee_charge}
 * — there is no charge for a concession to reference until fee demand exists. Defining the
 * catalogue of what a concession <em>is</em> (FR-075's "waivers"), on the other hand, is exactly
 * the same kind of setup work as defining a fee head, and needs nothing this lane does not already
 * have. Splitting the FR this way, rather than deferring FR-078 wholesale, is a deliberate call —
 * see the PR that introduced this module for the argument in full.
 *
 * <p><strong>Targets by class only.</strong> FR-076 also lists section, student category,
 * transport route, hostel, optional subject and individual student. Transport route and hostel are
 * deferred with the modules that would scope against them (08-phase-2-scope.md §"Fee structure",
 * matching the same reasoning `docs/status.md` already applied to the student record). Student
 * category, optional subject and individual-student targeting are deferred here too, and
 * deliberately: a per-student fee adjustment is a demand-time concern under ADR-0012 (a
 * {@code fee_charge} or a concession against one), not a base-structure concern, and the
 * "category" FR-076 names would otherwise either duplicate {@code student.domain.StudentCompliance}'s
 * Restricted, encrypted EWS/BPL/RTE field — which this module has no business reading, tier or
 * boundary — or invent a second, disconnected notion of category nobody asked for. Section-level
 * pricing (as opposed to class-level) is not a distinction any Phase 0 or Phase 2 document draws;
 * it is left for whoever builds fee demand to add if a real school asks for it.
 *
 * <p><strong>Session-scoping, and how an in-place edit is prevented.</strong> A {@code fee_structure}
 * row is never updated once written — see that class's own Javadoc for why this module goes
 * further than ADR-0012's letter requires. "Editing" a structure
 * ({@code FeeStructureService#save}) always writes the next version for the same
 * {@code (session, class)} and marks the previous one superseded, in one transaction. A version
 * may be added to a session that has already run its course exactly once — the very first, so a
 * school onboarding late can still record what it charged historically — and never again once one
 * exists, which is what actually stops a filed structure from being rewritten after the fact. See
 * {@code FeeStructureService} for the precise rule.
 *
 * <p>Reaches {@code academics} only through {@code academics.api.AcademicsLookup}, never a join or
 * a domain import — the same discipline {@code attendance} and {@code student} already follow.
 * Every table this module owns is per-tenant and carries no {@code school_id}: the PostgreSQL
 * schema is the tenant boundary (ADR-0011).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Fee")
package in.chalkbase.fee;
