package in.chalkbase.student.domain;

import in.chalkbase.platform.crypto.Encrypted;
import in.chalkbase.platform.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A student's health record (FR-034): CWSN/disability, allergies, chronic conditions, medication,
 * blood group, and who to call.
 *
 * <p><strong>Six columns are Restricted, encrypted at rest and masked by default in the UI</strong>
 * (ADR-0014, ADR-0022): {@link #getBloodGroup()}, {@link #getCwsnStatus()},
 * {@link #getDisabilityDetails()}, {@link #getAllergies()}, {@link #getChronicConditions()} and
 * {@link #getMedication()}. All six are health facts about a child under ADR-0014's Restricted
 * bucket — disability/CWSN is named there explicitly, and blood group is the arguable one: schools
 * print it on an ID card without a second thought, but it is still a health fact, and ADR-0014 says
 * to pick the more protective tier when it is arguable. {@code EncryptionBindingTests} fails the
 * build if any of the six ever loses its {@code @Encrypted} pairing with its DTO counterpart.
 *
 * <p>The emergency contact is <strong>Confidential, not Restricted</strong>: a name and a phone
 * number, the same tier as a guardian's, and not necessarily one of the guardians already on the
 * record — a neighbour or a relative may be the number the school is asked to ring first in an
 * emergency. Shown in full, unmasked, wherever the rest of the Confidential record is.
 *
 * <p>One row per student, present only once a school has entered something here. See
 * {@code EncryptionBindingTests}' own Javadoc: this is the field this test's fixtures were shaped
 * to anticipate, and none of its fixtures need touching now that this class exists for real.
 */
@Entity
@Table(name = "student_medical")
public class StudentMedical {

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    /** Restricted (ADR-0014): a health fact about a child. Encrypted at rest (ADR-0022). */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "blood_group", columnDefinition = "text")
    private String bloodGroup;

    /** Restricted: whether, and what kind of, CWSN/disability status this child has. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cwsn_status", columnDefinition = "text")
    private String cwsnStatus;

    /** Restricted: free-text detail behind {@link #cwsnStatus}. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "disability_details", columnDefinition = "text")
    private String disabilityDetails;

    /** Restricted: a health record. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String allergies;

    /** Restricted: a health record. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "chronic_conditions", columnDefinition = "text")
    private String chronicConditions;

    /** Restricted: a health record. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String medication;

    /** Confidential, not Restricted — a name, the same tier as a guardian's. Never masked. */
    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    /** Confidential. */
    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    /** Confidential. Free text: this contact need not be one of the guardians already on record. */
    @Column(name = "emergency_contact_relation", length = 60)
    private String emergencyContactRelation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StudentMedical() {
        // for JPA
    }

    public StudentMedical(
            UUID studentId,
            String bloodGroup,
            String cwsnStatus,
            String disabilityDetails,
            String allergies,
            String chronicConditions,
            String medication,
            String emergencyContactName,
            String emergencyContactPhone,
            String emergencyContactRelation) {
        this.studentId = studentId;
        apply(
                bloodGroup,
                cwsnStatus,
                disabilityDetails,
                allergies,
                chronicConditions,
                medication,
                emergencyContactName,
                emergencyContactPhone,
                emergencyContactRelation);
    }

    /** Overwrites every editable field, as a whole form — see {@link Student#apply}. */
    public final void apply(
            String bloodGroup,
            String cwsnStatus,
            String disabilityDetails,
            String allergies,
            String chronicConditions,
            String medication,
            String emergencyContactName,
            String emergencyContactPhone,
            String emergencyContactRelation) {
        this.bloodGroup = bloodGroup;
        this.cwsnStatus = cwsnStatus;
        this.disabilityDetails = disabilityDetails;
        this.allergies = allergies;
        this.chronicConditions = chronicConditions;
        this.medication = medication;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.emergencyContactRelation = emergencyContactRelation;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getCwsnStatus() {
        return cwsnStatus;
    }

    public String getDisabilityDetails() {
        return disabilityDetails;
    }

    public String getAllergies() {
        return allergies;
    }

    public String getChronicConditions() {
        return chronicConditions;
    }

    public String getMedication() {
        return medication;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getEmergencyContactRelation() {
        return emergencyContactRelation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
