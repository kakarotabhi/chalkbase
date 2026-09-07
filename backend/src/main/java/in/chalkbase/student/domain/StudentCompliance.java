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
 * The identifiers and statutory categories UDISE+ and the boards ask for (FR-029), plus the
 * Restricted columns ADR-0020 §2 deliberately left the student record without.
 *
 * <p><strong>Four columns are Restricted, encrypted at rest and masked by default in the UI</strong>
 * (ADR-0014, ADR-0022): {@link #getCasteCategory()}, {@link #getReligion()},
 * {@link #getSpecialCategory()} (EWS/BPL/RTE) and {@link #getApaarId()}. These are exactly the
 * columns ADR-0020 §2 named as blocked on encryption at rest existing, minus two ADR-0020 §2 also
 * named that are deliberately still absent: an Aadhaar reference, because FR-029 does not ask the
 * student record for one — only PEN/UDISE, APAAR and the board registration number — and guardian
 * income, because it belongs on {@code Guardian}, not here, and nothing in this slice's
 * requirements calls for it either.
 *
 * <p><strong>{@link #getApaarId()} may not be set without consent recorded</strong> — enforced in
 * {@code StudentRecordService}, not by a database constraint, because the constraint would have to
 * inspect a value that is encrypted before it ever reaches this table. {@link #isApaarConsentGiven()},
 * {@link #getApaarConsentGivenBy()} and {@link #getApaarConsentGivenAt()} are the consent record
 * ADR-0014 asks for: who consented, and when. The three are Internal/Confidential, not Restricted —
 * the fact that consent was given is metadata about a decision, not itself a caste, a religion or an
 * APAAR number.
 *
 * <p>{@link #getPenUdiseId()} and {@link #getBoardRegistrationNumber()} are Confidential, the same
 * tier as {@code student.admission_number} — identifiers, not the Restricted categories beside them.
 *
 * <p>One row per student, present only once a school has entered something for UDISE+ or the
 * boards.
 */
@Entity
@Table(name = "student_compliance")
public class StudentCompliance {

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    /** Confidential: an identifier, like an admission number. */
    @Column(name = "pen_udise_id", length = 40)
    private String penUdiseId;

    /** Confidential: an identifier, like an admission number. */
    @Column(name = "board_registration_number", length = 40)
    private String boardRegistrationNumber;

    /** Restricted (ADR-0014): caste and community. Encrypted at rest (ADR-0022). */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "caste_category", columnDefinition = "text")
    private String casteCategory;

    /** Restricted. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String religion;

    /** Restricted: EWS/BPL/RTE category. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "special_category", columnDefinition = "text")
    private String specialCategory;

    /** Restricted: APAAR requires its own consent record (ADR-0014) — see the class Javadoc. Encrypted at rest. */
    @Encrypted @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "apaar_id", columnDefinition = "text")
    private String apaarId;

    /** Internal: a fact about a decision, not a value of any Restricted category itself. */
    @Column(name = "apaar_consent_given", nullable = false)
    private boolean apaarConsentGiven;

    /** Confidential: a person's name. Who gave the consent {@link #apaarConsentGiven} records. */
    @Column(name = "apaar_consent_given_by", length = 200)
    private String apaarConsentGivenBy;

    /** Internal: when consent was recorded. Never blank when {@link #apaarConsentGiven} is true. */
    @Column(name = "apaar_consent_given_at")
    private Instant apaarConsentGivenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StudentCompliance() {
        // for JPA
    }

    public StudentCompliance(
            UUID studentId,
            String penUdiseId,
            String boardRegistrationNumber,
            String casteCategory,
            String religion,
            String specialCategory,
            String apaarId,
            boolean apaarConsentGiven,
            String apaarConsentGivenBy,
            Instant apaarConsentGivenAt) {
        this.studentId = studentId;
        apply(
                penUdiseId,
                boardRegistrationNumber,
                casteCategory,
                religion,
                specialCategory,
                apaarId,
                apaarConsentGiven,
                apaarConsentGivenBy,
                apaarConsentGivenAt);
    }

    /** Overwrites every editable field, as a whole form — see {@link Student#apply}. */
    public final void apply(
            String penUdiseId,
            String boardRegistrationNumber,
            String casteCategory,
            String religion,
            String specialCategory,
            String apaarId,
            boolean apaarConsentGiven,
            String apaarConsentGivenBy,
            Instant apaarConsentGivenAt) {
        this.penUdiseId = penUdiseId;
        this.boardRegistrationNumber = boardRegistrationNumber;
        this.casteCategory = casteCategory;
        this.religion = religion;
        this.specialCategory = specialCategory;
        this.apaarId = apaarId;
        this.apaarConsentGiven = apaarConsentGiven;
        this.apaarConsentGivenBy = apaarConsentGivenBy;
        this.apaarConsentGivenAt = apaarConsentGivenAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getPenUdiseId() {
        return penUdiseId;
    }

    public String getBoardRegistrationNumber() {
        return boardRegistrationNumber;
    }

    public String getCasteCategory() {
        return casteCategory;
    }

    public String getReligion() {
        return religion;
    }

    public String getSpecialCategory() {
        return specialCategory;
    }

    public String getApaarId() {
        return apaarId;
    }

    public boolean isApaarConsentGiven() {
        return apaarConsentGiven;
    }

    public String getApaarConsentGivenBy() {
        return apaarConsentGivenBy;
    }

    public Instant getApaarConsentGivenAt() {
        return apaarConsentGivenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
