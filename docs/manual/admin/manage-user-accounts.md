# Manage user accounts

Who holds an account at your school, and the four things you can do to one after it exists: create,
deactivate, reactivate, unlock, and reset a password.

**Who can do this:** the principal by default. A school that wants someone else — an office
administrator who runs the roster without deciding what any role may do — can grant "Manage user
accounts" on its own, separately from "Manage roles and permissions"; see
[manage roles and access](manage-roles-and-access.md).

## Steps

1. Open **User accounts**. If your role only lets you manage roles and not accounts, get here from
   the link on the **Roles and access** screen.
2. **Add an account**: type a username and the person's name, and save. A temporary password is
   generated for you — see below, this is the one moment you will ever see it.
3. **Deactivate** ends the account's access and every session it currently holds, immediately,
   everywhere it is signed in. Nothing about the account is lost, and **Reactivate** brings it back
   at any time.
4. **Clear lockout** removes a lockout from repeated wrong password attempts. It is always safe to
   select — if the account was not locked, nothing changes.
5. **Reset password** issues a new temporary password and, like deactivating, ends every session the
   account currently holds immediately.

## The temporary password is shown once

Creating an account and resetting a password both show a temporary password on the screen that
follows. **Write it down or copy it before you close that screen.** Nothing in Chalkbase stores it,
and there is no way to see it again — only another reset, which is a different password.

Hand it to the person directly. Never by email or chat, and never leave it visible on a shared
screen.

## Acting on your own account

You can deactivate or reset your own account like anyone else's — the screen tells you so before
you confirm. Doing either signs you out immediately, on this device too, because both end every
session the account holds. If you deactivate yourself, another administrator has to reactivate you;
if you reset your own password, sign in again with the one you were just shown.

## When it goes wrong

**"This would leave nobody at the school able to manage access."** You tried to deactivate the last
account that can manage roles and access. This is not a mistake on your part — Chalkbase is
protecting the school from locking itself out. Grant "Manage roles and permissions" to another
active account first, then try again.

**"That username is already in use at this school."** Someone already signs in with that username.
Pick another one; usernames cannot be changed later.

**"You do not have permission to manage user accounts."** Ask whoever manages roles at your school
to add "Manage user accounts" to your role. If you can only see the roster and not act on it, you
hold "View users" but not "Manage user accounts" — a school can grant either on its own.

## What is recorded

Every account created, deactivated, reactivated, unlocked or reset is written to the audit log: who
did it, to which account, and when. A password itself — temporary or otherwise — is never recorded,
in any form, anywhere in this log.
