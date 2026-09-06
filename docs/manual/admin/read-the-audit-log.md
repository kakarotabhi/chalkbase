# Read the audit log

Who did what, and when. Every sign-in, every refused attempt, and every change to a record is
written to your school's audit log, and nothing in Chalkbase can edit or delete an entry once it is
there.

**Who can do this:** anyone holding the Auditor role. It is deliberately not given to the principal
by default — reading who did what is oversight rather than a convenience — but a school that wants
its principal to have it can add "View the audit log" to that role.

## Steps

1. Open **Audit log** from the menu. If it is the only thing your account can open, signing in
   takes you straight to it.
2. The newest entries are first. Each line says when something happened, who did it, what they did,
   and which record it was about.
3. Narrow it down with the filters along the top:
   - **Action** — sign-ins, refused sign-ins, changes, and so on.
   - **From / To** — both dates are included. Picking 1 September to 5 September shows you the 5th.
4. To see everything one person did, select their name in any row. Clear it with the ✕ on the chip.
5. Select a row to see the address it came from, the device, and the trace id.

## What the log records, and what it does not

It records **which fields changed** — "address line 1, phone" — and never what they changed to.

This is deliberate and it is not a limitation to work around. Storing the old and new value of
every edit would build a second, permanent copy of every student record inside the audit log, which
is the last place anyone would think to protect it. If you need to know what a value used to be,
that belongs to the record itself, not to the log.

Passwords are never recorded, in any form, anywhere in this log.

## When it goes wrong

**"You do not have permission to view the audit log."** Your role does not include it. Ask whoever
manages roles at your school.

**"Nothing matches these filters."** The log is not empty — your filters are too narrow. Widen the
dates or set the action back to "Any action".

**A trace id.** If someone reports an error and quotes a trace id from the screen, search the log by
date around the time it happened; the entry with that trace id is the action they were attempting.

## Times

Times are shown using your own device's clock. Everyone in India is on the same clock, so this
matches the school — but if you are reading the log from another country, open a row to see the full
timestamp with its timezone named.
