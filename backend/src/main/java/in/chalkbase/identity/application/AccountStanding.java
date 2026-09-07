package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.AccountStatus;
import java.time.Instant;

/**
 * What a session is allowed to do, re-read on every request (ADR-0023).
 *
 * <p>Three columns, one indexed primary-key read — the same query {@code PasswordChangeRequiredFilter}
 * (now folded into {@code SessionStandingFilter}) always paid for {@code mustChangePassword} alone,
 * projecting one more enum and one more timestamp costs nothing extra in round trips. This is
 * deliberately <strong>not</strong> the effective permission set: that stays resolved once at login
 * per ADR-0005, because recomputing it here would mean a join across {@code user_role_grant},
 * {@code role} and {@code role_permission} on every call, which is the per-request permission query
 * that ADR-0005 rejected outright. Account status and lockout are a single indexed column each, which
 * is the entire reason they are affordable to re-check here and permissions are not.
 *
 * @param status whether the account may be used at all
 * @param lockedUntil when a temporary lockout expires, or null if there is none in force
 * @param mustChangePassword whether the account is still on a school-issued password
 */
public record AccountStanding(AccountStatus status, Instant lockedUntil, boolean mustChangePassword) {

    /**
     * The fail-closed answer for an account the session points at that no longer exists.
     *
     * <p>A session surviving the deletion of its own account is not a session that should keep
     * reading a school's data while anyone decides what to call it — the same reasoning
     * {@link UserAccountService#mustChangePassword} already applied to this exact case.
     */
    static AccountStanding accountGone() {
        return new AccountStanding(AccountStatus.DISABLED, null, true);
    }

    /** False once the account is disabled, or while a lockout from repeated failed attempts stands. */
    public boolean isUsable(Instant now) {
        return status == AccountStatus.ACTIVE && (lockedUntil == null || !lockedUntil.isAfter(now));
    }
}
