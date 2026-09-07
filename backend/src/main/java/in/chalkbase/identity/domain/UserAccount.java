package in.chalkbase.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One human who may sign in to this school.
 *
 * <p>Deliberately unqualified: this lives in the school's own schema and is reached through
 * {@code search_path}. There is no {@code school_id} column, because the schema is the tenant
 * boundary (ADR-0011), and no password column, because proof lives in {@link UserCredential}.
 *
 * <p>The table is {@code user_account} rather than {@code user}: PostgreSQL accepts
 * {@code create table user} and then silently resolves {@code select * from user} to the
 * {@code current_user} function instead of the table (ADR-0017).
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status = AccountStatus.ACTIVE;

    /** A school issues a temporary password; the account cannot be used for anything else until it is changed. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Denormalized alongside the audit log's own record of the same event; never the source of truth. */
    @Column(name = "password_reset_at")
    private Instant passwordResetAt;

    @Column(name = "password_reset_by")
    private UUID passwordResetBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserAccount() {
        // for JPA
    }

    public UserAccount(String displayName) {
        this.displayName = displayName;
    }

    /** A lock expires on its own — a parent who mistyped is not stranded until the office opens. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void recordSuccessfulLogin(Instant now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }

    /**
     * Counts one failure and locks the account once {@code maxAttempts} have failed.
     *
     * @return true when <em>this</em> failure is the one that locked the account. The caller needs
     *     to tell "locked just now" from "already locked" so the audit log records one
     *     {@code ACCOUNT_LOCKED} per lockout rather than one per subsequent attempt (ADR-0018).
     */
    public boolean recordFailedAttempt(Instant now, int maxAttempts, Duration lockFor) {
        boolean wasLocked = isLocked(now);
        this.failedAttempts = (short) Math.min(this.failedAttempts + 1, Short.MAX_VALUE);
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = now.plus(lockFor);
        }
        return !wasLocked && isLocked(now);
    }

    public void passwordChanged() {
        this.mustChangePassword = false;
    }

    /** Admin-triggered deactivation. Idempotent: disabling an already-disabled account changes nothing. */
    public boolean disable() {
        if (status == AccountStatus.DISABLED) {
            return false;
        }
        this.status = AccountStatus.DISABLED;
        return true;
    }

    /** Idempotent: reactivating an already-active account changes nothing. */
    public boolean reactivate() {
        if (status == AccountStatus.ACTIVE) {
            return false;
        }
        this.status = AccountStatus.ACTIVE;
        return true;
    }

    /**
     * Clears a lockout early and resets the counter that would otherwise re-trigger it on the next
     * wrong guess. Idempotent: unlocking an account that was not locked changes nothing.
     */
    public boolean clearLockout() {
        if (lockedUntil == null && failedAttempts == 0) {
            return false;
        }
        this.lockedUntil = null;
        this.failedAttempts = 0;
        return true;
    }

    /** Issues a new school-set password: sets {@code mustChangePassword} and records who and when. */
    public void recordAdminPasswordReset(Instant now, UUID resetByAccountId) {
        this.mustChangePassword = true;
        this.passwordResetAt = now;
        this.passwordResetBy = resetByAccountId;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getPasswordResetAt() {
        return passwordResetAt;
    }

    public UUID getPasswordResetBy() {
        return passwordResetBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
