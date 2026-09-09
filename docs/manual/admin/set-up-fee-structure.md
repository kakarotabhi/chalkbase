# Set up the fee structure

What this school charges, and what each class pays for an academic session. This has to be in
place before a fee demand can be raised — collecting fees is a separate page, written once that
part of the system ships.

**Who can do this:** the principal, the vice principal, and the accountant for fee heads and the
structure itself. Only the principal and the vice principal define concession _types_ — what kinds
of waiver this school recognises. The admission counsellor can see fee heads and structures, to
answer a parent's question, but cannot change either.

## Add your fee heads first

1. Open **Fees → Fee heads**.
2. Select **Add a fee head**, name it the way it should read on a receipt — "Tuition Fee",
   "Computer Lab Fee" — and choose which of the seven kinds it is: tuition, admission,
   annual/development, transport, exam, activity, or late fee.
3. If it is an annual/development fee and your state caps it as a percentage of tuition, enter that
   percentage. Chalkbase will refuse a class structure that charges more than the cap allows.

A fee head is not tied to any one year — add it once and it carries forward. **Nothing here is ever
deleted.** A head already used in a class's structure is retired ("Stop offering") rather than
removed, and keeps its name while retired.

## Add concession types, if your school offers any

**Fees → Fee heads** also lists concession types — sibling discount, staff-child discount,
management quota, RTE/EWS, scholarship, or something else. Adding one here only records that this
school recognises that kind of waiver; it does not give it to any student. Granting a concession to
a particular child's fee happens when a fee demand exists, which is not built yet.

By default a concession type needs someone's approval before it is applied, because it is money the
school is giving up. Turn that off only for a waiver your school genuinely never wants a second
sign-off on.

## Set up a class's structure for a session

1. Open **Fees → Fee structure**. Choose the academic session at the top — it defaults to whichever
   one is current.
2. Find the class in the list and select **Set up** (or **Edit**, if it already has one).
3. Add a fee head, its amount, how often it is collected (one-time, monthly, quarterly, term-wise,
   annual, or custom), and the due date or dates that amount is split across. Add another fee head
   the same way for anything else this class pays.
4. Select **Save**.

**Saving never changes what was there before.** It writes a brand new version of that class's
structure and puts the old one aside, unchanged. If an inspector or an auditor ever asks what a
class was charged two years ago, the old version is still there exactly as it was — nothing in
Chalkbase can quietly rewrite it.

## Once a session has run its course

A session that is no longer current, and has already started, cannot be given a second version of
its structure. If you need to correct last year's fees, you cannot — that year is closed, on
purpose, the same way a filed document cannot be secretly edited after the fact. The one exception:
a class that never had a structure recorded at all for a past session can still have its very first
version entered, so a school catching up on its records can do so honestly, once.

## Starting a new session

A new academic year does not inherit last year's structure on its own — an empty session and a
silently copied one are both wrong in their own way, one making you retype everything, the other
risking a class being billed last year's amount by mistake. Instead:

1. On the **Fees → Fee structure** screen, with the new session chosen at the top, find **Copy
   from a previous session**.
2. Choose the session to copy from and select **Copy**.

Every class that had a structure in that session and does not already have one in this one is
copied across, with its due dates shifted onto the new session's calendar. A class you have already
set up by hand in this session is left exactly as you set it up — copying never overwrites it. The
screen tells you afterwards which classes were copied and which were left alone.

Copying gives you a starting point, not a finished job. Go through the copied classes and adjust
whatever changed — most schools change a few amounts each year and keep the rest.

## What is recorded

Every fee head, concession type and structure change goes into the audit log: who made it, and
which fields changed. The log never records the amount itself, only that a change happened —
the structure's own version history is what shows what the amount actually was.

## Related

Pages not written yet — linked once they exist:

- Collect a fee payment
- Raise a fee demand
- Apply a concession
