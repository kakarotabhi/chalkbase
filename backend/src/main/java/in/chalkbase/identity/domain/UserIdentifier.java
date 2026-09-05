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
 * Who someone is: a username, an email address or a phone number they sign in with.
 *
 * <p>Unique per {@code (type, value)} within one school's schema only. Two schools may both have a
 * parent whose username is the admission number {@code 2026-0412}, which is the point of accounts
 * living per tenant (ADR-0017).
 */
@Entity
@Table(name = "user_identifier")
public class UserIdentifier {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private IdentifierType type;

    @Column(name = "value", nullable = false, length = 320)
    private String value;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserIdentifier() {
        // for JPA
    }

    public UserIdentifier(UserAccount account, IdentifierType type, String value) {
        this.account = account;
        this.type = type;
        this.value = value;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getAccount() {
        return account;
    }

    public IdentifierType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
