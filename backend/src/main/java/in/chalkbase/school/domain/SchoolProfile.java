package in.chalkbase.school.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * What a school says about itself: where it is, who runs it, and how it is reached.
 *
 * <p>Deliberately unqualified, unlike {@link School}. The registry row in {@code public.school} is
 * identity and routing — read before any tenant is bound — while this is the school's own data and
 * lives in the school's own schema, reached through {@code search_path} (ADR-0011). There is no
 * {@code school_id}: the schema is the tenant.
 *
 * <p>There is at most one of these per schema, and {@code uq_school_profile_singleton} is what says
 * so. The mapped {@code singleton} field is only there so Hibernate writes the column; nothing
 * reads it.
 */
@Entity
@Table(name = "school_profile")
public class SchoolProfile {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Always true. The unique constraint on this column is what makes the table a singleton. */
    @Column(name = "is_singleton", nullable = false, updatable = false)
    private boolean singleton = true;

    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(name = "principal_name", nullable = false, length = 200)
    private String principalName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 200)
    private String website;

    @Column(name = "affiliation_number", length = 40)
    private String affiliationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Board board;

    /**
     * An IANA zone id, e.g. {@code Asia/Kolkata}. Authoritative here, not in {@code public.school}
     * — this is the school's own editable data, and the registry keeps only a copy for the sessions
     * and lookups that run before a tenant is bound (see {@code School#timezone}). Every date and
     * time this school's staff, parents and students read is rendered in it.
     */
    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SchoolProfile() {
        // for JPA
    }

    public SchoolProfile(
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String pincode,
            String principalName,
            String phone,
            String email,
            String website,
            String affiliationNumber,
            Board board,
            String timezone) {
        apply(
                addressLine1,
                addressLine2,
                city,
                state,
                pincode,
                principalName,
                phone,
                email,
                website,
                affiliationNumber,
                board,
                timezone);
    }

    /**
     * Overwrites every editable field.
     *
     * <p>One method rather than eleven setters: the profile is edited as a whole form, and a
     * partial update would let a screen that forgot a field silently blank it.
     */
    public final void apply(
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String pincode,
            String principalName,
            String phone,
            String email,
            String website,
            String affiliationNumber,
            Board board,
            String timezone) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.principalName = principalName;
        this.phone = phone;
        this.email = email;
        this.website = website;
        this.affiliationNumber = affiliationNumber;
        this.board = board;
        this.timezone = timezone;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPincode() {
        return pincode;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getWebsite() {
        return website;
    }

    public String getAffiliationNumber() {
        return affiliationNumber;
    }

    public Board getBoard() {
        return board;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
