# ADR-0013: Payments and messaging are ports; v1 registers the cheapest adapters

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0012](0012-fee-ledger-model.md), [ADR-0001](0001-modular-monolith.md)

## Context

Two Phase 0 questions — which payment gateway, which SMS and WhatsApp provider — were answered with
"later". That is a legitimate answer, but only if "later" costs an adapter rather than a migration.

The reasons for deferring are different in each case and both are worth recording.

**Payments.** The pilot school collects fees at a counter today. Online payment is genuinely valuable
and Epic 5 has the story for it, but a gateway brings settlement reconciliation, refund flows,
webhook handling and a merchant onboarding process, none of which are needed to run a school's first
term on the system.

**Messaging.** Transactional SMS in India requires TRAI DLT registration: an entity registration, a
sender ID, and per-template approval, each taking weeks and none of which can be started
retroactively. WhatsApp needs a Business Solution Provider and template approval on top. Blocking v1
on that paperwork would idle the build.

The risk in both cases is identical, and it is not the missing feature. It is that a system built
without online payment quietly grows an assumption that payment is synchronous and always succeeds,
and a system built without SMS grows an assumption that notification is best-effort email. Those
assumptions are cheap to prevent and expensive to remove.

## Options considered

1. **Build the integrations now anyway.** Removes the future work, but spends weeks on merchant
   onboarding and DLT paperwork before the product can be demonstrated, and locks in a provider
   before the pilot school's bank has an opinion. Rejected.

2. **Call the providers directly when they arrive, no abstraction now.** Least code today. But the
   call sites that would need changing are spread across fee collection, admissions, attendance
   alerts and circulars, and the data model would have no place to put a gateway reference or a
   delivery status. Rejected — this is the version where "later" costs a migration.

3. **Define the ports now, register only the adapters v1 needs.** The interfaces, the data model and
   the call sites are built for the full picture; the implementations behind them are the trivial
   ones.

## Decision

**Option 3. Two ports, defined now. v1 registers only what it needs, and the data model is complete
from the start.**

### `PaymentGateway`

v1 registers **`OfflinePayment`** only — cash, cheque, NEFT and UPI collected at the counter and
recorded by an operator. **Razorpay is the intended first online adapter**, chosen for its sandbox
quality, idempotent webhooks and UPI Autopay for recurring fees; nothing depends on that yet.

The model is built gateway-ready now, which is the part that matters:

- `payment_intent` — amount, purpose, student, status, created and expiry. An offline payment creates
  and immediately settles one; an online payment settles it on a webhook.
- `gateway_reference` — provider name, provider order and payment ids, and the raw response stored
  as received.
- `webhook_receipt` — provider event id **unique**, so a replayed webhook is a no-op. The MVP
  acceptance criteria require idempotent webhooks; building the uniqueness constraint before there is
  a webhook is nearly free, and retrofitting it onto live payment data is not.

Payment capture stays separate from the fee ledger ([ADR-0012](0012-fee-ledger-model.md)). The ledger
records that money arrived; the port records how.

### `NotificationChannel`

v1 registers **transactional email** and **web push** (VAPID, to the PWA). `SMS` and `WHATSAPP` are
declared channel types with no registered adapter — a message routed to them is recorded as
`UNSUPPORTED_CHANNEL`, visibly, rather than silently dropped.

- A notification is **queued and sent asynchronously**, never inline with a request. Attendance
  alerts for a whole class must not sit in an HTTP transaction.
- Every message carries a **per-recipient delivery status** — queued, sent, delivered, failed,
  unsupported — because Epic 6 requires delivery tracking, and because a fee reminder nobody received
  is a dispute.
- Message content comes from **named templates with typed parameters**, not string concatenation at
  the call site. DLT approval is per template; a system that composes messages ad hoc cannot be made
  DLT-compliant later without rewriting every call site.
- Templates are multilingual from the start (English plus Hindi for Delhi), since retrofitting a
  language column into approved templates means re-approval.

### What is explicitly accepted

Indian parents read SMS and WhatsApp; they do not reliably read email. Absence alerts and fee
reminders will have **materially lower reach in v1**, and the pilot school must be told that rather
than discovering it.

**TRAI DLT registration starts now**, decoupled from when SMS ships. It is weeks of paperwork on the
critical path of both fee reminders and any future phone-OTP login.

## Consequences

**Easier.** v1 has no merchant onboarding, no DLT dependency and no provider contract. Adding
Razorpay is one adapter plus a settlement reconciliation screen, with no schema change. Adding SMS is
one adapter plus template registration. The offline adapter is not throwaway scaffolding — counter
collection remains real for the life of the product.

**Harder.** Two abstractions exist before either has a second implementation, which is a shape worth
being suspicious of in general; it is justified here by the data model needing to be right first,
not by the interface itself. Delivery status and webhook receipt tables carry rows that mean little
in v1. Parent-facing communication is weaker than the market expects until SMS lands.

**To revisit.** Choose the payment provider when the pilot school's bank and settlement account are
known, not before. Choose the SMS and WhatsApp provider once DLT entity registration completes —
that process reveals which providers are painless. If a third adapter is ever added to either port
and the interface fights it, the interface is wrong and should change; two implementations is not
enough evidence that an abstraction is correct.
