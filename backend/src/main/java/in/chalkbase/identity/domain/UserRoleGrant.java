package in.chalkbase.identity.domain;

import in.chalkbase.platform.security.AccessScope;
import in.chalkbase.platform.security.ScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * "This person holds this role, over this much of the school, for this long" (ADR-0005).
 *
 * <p>A user holds <strong>several</strong> of these, and that is the point. There is no "teacher
 * who is also transport in-charge" role; there is a class-teacher grant and a transport grant.
 * Adding a responsibility is a row, not a new role, which is what stops role names multiplying
 * combinatorially as schools are onboarded.
 *
 * <p>The validity window exists from the start because schools genuinely need "acting principal for
 * March". A grant outside its window contributes nothing at all — it is not a weaker grant, it is
 * absent.
 */
@Entity
@Table(name = "user_role_grant")
public class UserRoleGrant {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /**
     * A plain id rather than an association: nothing here ever needs to read the account, and a
     * {@code @ManyToOne} would drag one into every permission resolution.
     */
    @Column(name = "user_account_id", nullable = false)
    private UUID userAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private ScopeType scopeType;

    /** Null for {@code SCHOOL} and {@code SELF}, which need no target. */
    @Column(name = "scope_id")
    private UUID scopeId;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserRoleGrant() {
        // for JPA
    }

    public UserRoleGrant(UUID userAccountId, Role role, ScopeType scopeType, UUID scopeId) {
        this(userAccountId, role, scopeType, scopeId, null, null);
    }

    public UserRoleGrant(
            UUID userAccountId, Role role, ScopeType scopeType, UUID scopeId, LocalDate validFrom, LocalDate validTo) {
        this.userAccountId = userAccountId;
        this.role = role;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    /**
     * Both bounds are inclusive and either may be absent. "Acting principal for March" ends on the
     * 31st and is still in force that day.
     */
    public boolean isInForceOn(LocalDate date) {
        return (validFrom == null || !validFrom.isAfter(date)) && (validTo == null || !validTo.isBefore(date));
    }

    public AccessScope scope() {
        return new AccessScope(scopeType, scopeId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    public Role getRole() {
        return role;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
