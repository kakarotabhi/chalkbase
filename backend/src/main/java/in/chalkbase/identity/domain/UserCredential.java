package in.chalkbase.identity.domain;

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
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * How someone proves who they are.
 *
 * <p>{@code secret} holds a hash carrying its own algorithm prefix (Spring Security's
 * {@code DelegatingPasswordEncoder} format), so the algorithm can be upgraded by re-hashing on the
 * next login rather than by a migration. It is never logged and never leaves this module.
 *
 * <p>A superseded credential is revoked rather than deleted; a partial unique index keeps at most
 * one ACTIVE credential of each type per account.
 */
@Entity
@Table(name = "user_credential")
public class UserCredential {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private CredentialType type;

    @Column(name = "secret", length = 512)
    private String secret;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CredentialStatus status = CredentialStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected UserCredential() {
        // for JPA
    }

    public UserCredential(UserAccount account, CredentialType type, String secret) {
        this.account = account;
        this.type = type;
        this.secret = secret;
    }

    /** Replaces the stored proof in place. The caller supplies an already-encoded value. */
    public void replaceSecret(String encodedSecret) {
        this.secret = encodedSecret;
    }

    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    public void revoke() {
        this.status = CredentialStatus.REVOKED;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getAccount() {
        return account;
    }

    public CredentialType getType() {
        return type;
    }

    /** The stored hash. Never log this, never put it in a response. */
    public String getSecret() {
        return secret;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
