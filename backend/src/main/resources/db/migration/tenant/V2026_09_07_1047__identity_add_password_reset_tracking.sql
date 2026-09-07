-- Who last reset an account's password with an admin action, and when.
--
-- The audit log already records this (actor_id, occurred_at on the PASSWORD_RESET_BY_ADMIN row),
-- so these two columns are a denormalized convenience — a user-management screen can show "last
-- reset by X on Y" against the account itself without joining the audit log — never the source of
-- truth. `password_reset_by` is nullable rather than dropped on the resetting admin's own account
-- being deactivated later: the fact that a reset happened, and roughly by whom, should survive that.

alter table user_account
    add column password_reset_at timestamptz,
    add column password_reset_by uuid references user_account (id) on delete set null;

comment on column user_account.password_reset_at is
    'When an administrator last issued a temporary password for this account. Null if never.';
comment on column user_account.password_reset_by is
    'The admin account that last did so. Null if never, or if that account has since been deleted.';
