# Manage roles and access

What each role at your school may do, and who holds it. A role is a named bundle of permissions —
Class Teacher, Accountant, Front Office — and every permission it carries, it hands to everyone
granted that role.

**Who can do this:** the principal by default. Chalkbase ships twelve starting roles; every school
copies and edits them rather than starting from nothing, and can invent new ones of its own.

## Steps

1. Open **Roles and access**.
2. **Add a role**: give it a name and, optionally, a description, then check the permissions it
   should carry from the catalogue below, grouped by area. Save.
3. **Edit permissions** on an existing role replaces its whole permission set with whatever is
   checked when you save — not only the boxes you changed. Review the whole list before saving, not
   just the one you came to change.
4. **Who holds this** shows every account currently granted a role, so you can see who would be
   affected before you edit it.
5. **Grant and revoke**, further down the screen: choose an account, then grant it a role — over the
   whole school, or narrowed to one class, section or subject — or revoke one it already holds.

## You cannot hand out access you do not have yourself

A box marked **"not held by you"** cannot be _added_ to a role by you, because Chalkbase will not
let you grant a permission you do not hold yourself — this stops any one person quietly widening
their own reach through a role they edit. You can still _remove_ such a permission from a role;
taking access away is never restricted this way.

If you try anyway, the screen tells you exactly which permission is the problem before it even asks
the server, so you are not left guessing which of several checked boxes was refused.

## Scope: how much of the school a grant covers

Most grants are for the whole school. A grant can also be narrowed to one class, one section or one
subject — for a subject teacher who should only see their own sections, say. Two scopes are not yet
offered here — one campus, one department — because nothing in Chalkbase yet keeps a list of a
school's campuses or departments to choose from; that arrives once one does.

## When it goes wrong

**"This would leave nobody at the school able to manage access."** You tried to remove "Manage roles
and permissions" from a role, or revoke a grant, that is the last thing standing between the school
and being locked out of its own access. This is not a mistake — Chalkbase is protecting the school.
Grant that permission to another active account first.

**"You cannot grant this because you do not hold it yourself."** See above. Ask someone who holds
the permission to add it to the role instead, or add it to your own role first if you are meant to
have it.

**"A role with a name this close to that one already exists."** Roles are matched loosely by name;
rename the new one.

**"That role is already granted."** The account already holds this exact role at this exact scope.

**"You do not have permission to view roles."** Ask whoever manages roles at your school — which,
if you are reading this because nobody can, is the situation the first error above exists to
prevent.

## What is recorded

Every role created or edited, and every grant made or revoked, is written to the audit log: who did
it, to which role or account, and when.
