# ADR-0012: The fee ledger is append-only

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0006](0006-configurability-model.md), [ADR-0011](0011-schema-per-tenant.md),
  [ADR-0013](0013-external-provider-ports.md)

## Context

Fees are the module a school will not forgive us for getting wrong. The roadmap's risk table names
it directly: *"weak fee ledger design → financial mismatch and loss of trust"*, and the MVP
acceptance criteria require that the fee ledger passes reconciliation tests.

The pressure comes from how Indian school fees actually behave over a session, not from volume. In
one year a single student can plausibly have: a fee structure applied in April, a sibling concession
approved in May, a part payment in June, transport added in July and dropped in November, a late fee
in August, a cheque that bounces in September, a refund on withdrawal in January, and a fee revision
the school had to file with the Delhi Directorate of Education somewhere in the middle.

Every one of those is a *later* fact about an *earlier* obligation. A design that stores "amount due"
as a number and updates it has thrown away the information needed to explain the number — and the
question an accountant asks is never "what is the balance", it is "why is the balance that".

Delhi adds a second requirement. The DoE requires fee structures to be filed and approved, and caps
Development Fee as a proportion of tuition. That means the fee structure a school charged in a given
session has to be reproducible after the fact, not merely current.

## Options considered

1. **Invoice with a status column.** One row per student per installment, with `amount`, `paid` and
   a status. Fastest to build and the shape most people reach for first.
   Concessions, part payments, refunds and mid-year transport changes all become **edits to a
   settled invoice**. Once a row has been edited there is no way to answer "what did we charge and
   what changed", so reconciliation is not merely hard, it is impossible in principle. Rejected.

2. **Full double-entry accounting.** A real chart of accounts, debit and credit journal lines,
   trial balance. Unambiguously the most correct model, and it exports cleanly to Tally.
   It is also an accounting product living inside a school product: several extra weeks of work, a
   vocabulary the school's front-desk staff do not have, and school accountants will still demand a
   fee-centric screen on top of it. The generality buys nothing that option 3 does not, at this
   scale. Deferred to Phase 4 as an **export layer**, not a storage model.

3. **Ledger-style: immutable charges plus signed adjustment rows.** Domain-specific rather than
   accounting-general, but with double-entry's essential property — nothing is ever overwritten.

## Decision

**Option 3. The fee ledger is append-only. A balance is a sum, never a stored field.**

### Structure

```
fee_head            tuition, admission, annual/development, transport, exam, activity, late fee
   ↓                a head carries a category, and may carry a cap rule (Delhi: development ≤ 15% of tuition)
fee_structure       per class × session × category, composed of heads and installment due dates
   ↓                versioned by session — a past session's structure is never edited
fee_demand          raising a demand for a student generates immutable charge rows
   ↓
fee_charge          IMMUTABLE. student, head, session, amount, due date, source structure version
   ↑
fee_ledger_entry    signed rows referencing charges: payment, concession, refund, write-off,
                    late fee, reversal. Never updated, never deleted.
```

### Rules

1. **A `fee_charge` row is never updated and never deleted.** Nor is a `fee_ledger_entry`.
2. **Balance is computed**: `sum(charges) - sum(ledger entries)`, per student, per head, per session.
   No `amount_due` column exists to drift out of step.
3. **A correction is a reversal entry**, carrying a reason and the id of the entry it reverses.
4. **Receipts are immutable and gapless**, numbered per session from a per-school series. A receipt
   that was wrong is cancelled by a credit note that references it; the original stays.
5. **Concessions require approval** and are recorded with approver, reason and timestamp — they are
   money given away, and Epic 5 requires the approval step.
6. **Fee structures are session-scoped**, per [ADR-0006](0006-configurability-model.md) Tier 2. Last
   year's fee structure renders last year's receipts.
7. **Money is `numeric(12,2)`.** Never a float, in the database, in Java, or over the wire.
8. **Payment capture is a separate concern** from the ledger. A payment entry may reference a
   `payment_intent` owned by the payment port ([ADR-0013](0013-external-provider-ports.md)); the
   ledger does not know or care whether cash, cheque or a gateway produced it.

## Consequences

**Easier.** Reconciliation becomes arithmetic over an immutable log rather than an investigation.
"Why does this student owe ₹4,200" is answerable by listing rows. An audit trail is a
side effect of the storage model rather than a feature bolted on, which matters given FR-008 and
`AGENTS.md` rule 9. Adding Razorpay later touches payment capture only, not the ledger. Delhi's
filed-structure and Development Fee cap requirements are satisfied by the session versioning that was
needed anyway.

**Harder.** Every balance is an aggregate, so the collection and dues reports that management looks
at daily need indexed aggregate queries and, eventually, a materialised summary. That summary is a
**cache, and must be derivable** — the moment anyone treats it as the source of truth this ADR has
been undone. Correcting a mistake takes two rows and an approval instead of an edit, and school staff
used to editable spreadsheets will find that slow; that friction is the product working as intended.

**To revisit.** If the dues report becomes too slow at real volume, add a materialised per-student
balance refreshed on write — never a hand-maintained column. When Tally export is requested in Phase
4, build a projection from the ledger into double-entry journal lines rather than changing how fees
are stored.
