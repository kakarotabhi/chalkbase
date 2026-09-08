package in.chalkbase.student.application;

import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditOutcome;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.student.api.ComplianceDetail;
import in.chalkbase.student.api.ComplianceSummary;
import in.chalkbase.student.api.ContactDetail;
import in.chalkbase.student.api.MedicalDetail;
import in.chalkbase.student.api.MedicalSummary;
import in.chalkbase.student.api.PreviousSchoolDetail;
import in.chalkbase.student.api.SaveComplianceRequest;
import in.chalkbase.student.api.SaveContactRequest;
import in.chalkbase.student.api.SaveMedicalRequest;
import in.chalkbase.student.api.SavePreviousSchoolRequest;
import in.chalkbase.student.domain.StudentAudit;
import in.chalkbase.student.domain.StudentCompliance;
import in.chalkbase.student.domain.StudentContact;
import in.chalkbase.student.domain.StudentErrorCode;
import in.chalkbase.student.domain.StudentMedical;
import in.chalkbase.student.domain.StudentTransfer;
import in.chalkbase.student.infrastructure.StudentComplianceRepository;
import in.chalkbase.student.infrastructure.StudentContactRepository;
import in.chalkbase.student.infrastructure.StudentMedicalRepository;
import in.chalkbase.student.infrastructure.StudentRepository;
import in.chalkbase.student.infrastructure.StudentTransferRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four sections of the student record beyond the core fields, enrolment and guardians
 * (ADR-0020, FR-028): contact, previous school and transfer certificate, medical, and the
 * UDISE+/board compliance identifiers and categories.
 *
 * <p>A service of its own rather than more methods on {@link StudentService}, because two of these
 * four sections carry Restricted, encrypted fields and two do not, and the masking and read-auditing
 * rules that follow from that (ADR-0014, ADR-0022) are a distinct enough concern to keep apart from
 * enrolment and the core record.
 *
 * <p><strong>Masking is a server decision, not a display one.</strong> {@link #medicalSummary} and
 * {@link #complianceSummary} — what an ordinary {@code GET} answers with — never carry the six
 * health fields or the four caste/religion/category/APAAR fields; they carry only whether each has
 * been recorded. {@link #revealMedical} and {@link #revealCompliance} are the only methods in this
 * class that decrypt those fields, and they are the only reads in this module that call
 * {@link AuditService#recordSecurityEvent} — see {@link StudentAudit#RESTRICTED_DATA_REVEALED} for
 * why opening a student's record is not itself an audited read.
 *
 * <p>Every write here follows {@link StudentService}'s own rule: diffed before the entity is
 * mutated, audited with field NAMES only, and nothing written or audited when nothing changed.
 */
@Service
@Transactional(readOnly = true)
public class StudentRecordService {

    private final StudentRepository students;
    private final StudentContactRepository contacts;
    private final StudentTransferRepository transfers;
    private final StudentMedicalRepository medicals;
    private final StudentComplianceRepository compliances;
    private final AuditService audit;

    public StudentRecordService(
            StudentRepository students,
            StudentContactRepository contacts,
            StudentTransferRepository transfers,
            StudentMedicalRepository medicals,
            StudentComplianceRepository compliances,
            AuditService audit) {
        this.students = students;
        this.contacts = contacts;
        this.transfers = transfers;
        this.medicals = medicals;
        this.compliances = compliances;
        this.audit = audit;
    }

    // ── Contact (FR-028) ─────────────────────────────────────────────────────────────────────

    /** This student's address, phone and email, or null when nothing has been entered yet. */
    public ContactDetail contact(UUID studentId) {
        return contacts.findById(studentId).map(ContactDetail::of).orElse(null);
    }

    @Transactional
    public ContactDetail saveContact(UUID studentId, SaveContactRequest request) {
        requireStudent(studentId);
        Optional<StudentContact> existing = contacts.findById(studentId);
        String address = blankToNull(request.address());
        String phone = blankToNull(request.phone());
        String email = blankToNull(request.email());

        if (existing.isEmpty()) {
            if (address == null && phone == null && email == null) {
                // Nothing to create and nothing to say happened.
                return new ContactDetail(null, null, null);
            }
            StudentContact created = contacts.saveAndFlush(new StudentContact(studentId, address, phone, email));
            audit.recordChange(
                    AuditAction.ENTITY_CREATED,
                    StudentAudit.STUDENT_CONTACT,
                    studentId.toString(),
                    presentFields("address", address, "phone", phone, "email", email));
            return ContactDetail.of(created);
        }

        StudentContact contact = existing.get();
        Set<String> changed = new LinkedHashSet<>();
        addIfChanged(changed, "address", contact.getAddress(), address);
        addIfChanged(changed, "phone", contact.getPhone(), phone);
        addIfChanged(changed, "email", contact.getEmail(), email);
        if (changed.isEmpty()) {
            return ContactDetail.of(contact);
        }
        contact.apply(address, phone, email);
        contacts.saveAndFlush(contact);
        audit.recordChange(AuditAction.ENTITY_UPDATED, StudentAudit.STUDENT_CONTACT, studentId.toString(), changed);
        return ContactDetail.of(contact);
    }

    // ── Previous school and transfer certificate (FR-033) ───────────────────────────────────

    public PreviousSchoolDetail previousSchool(UUID studentId) {
        return transfers.findById(studentId).map(PreviousSchoolDetail::of).orElse(null);
    }

    @Transactional
    public PreviousSchoolDetail savePreviousSchool(UUID studentId, SavePreviousSchoolRequest request) {
        requireStudent(studentId);
        Optional<StudentTransfer> existing = transfers.findById(studentId);
        String schoolName = blankToNull(request.previousSchoolName());
        String board = blankToNull(request.previousSchoolBoard());
        String tcNumber = blankToNull(request.transferCertificateNumber());
        String reason = blankToNull(request.reasonForLeaving());

        if (existing.isEmpty()) {
            if (schoolName == null
                    && board == null
                    && tcNumber == null
                    && request.transferCertificateIssuedOn() == null
                    && reason == null) {
                return new PreviousSchoolDetail(null, null, null, null, null);
            }
            StudentTransfer created = transfers.saveAndFlush(new StudentTransfer(
                    studentId, schoolName, board, tcNumber, request.transferCertificateIssuedOn(), reason));
            audit.recordChange(
                    AuditAction.ENTITY_CREATED,
                    StudentAudit.STUDENT_TRANSFER,
                    studentId.toString(),
                    presentFields(
                            "previousSchoolName", schoolName,
                            "previousSchoolBoard", board,
                            "transferCertificateNumber", tcNumber,
                            "transferCertificateIssuedOn", request.transferCertificateIssuedOn(),
                            "reasonForLeaving", reason));
            return PreviousSchoolDetail.of(created);
        }

        StudentTransfer transfer = existing.get();
        Set<String> changed = new LinkedHashSet<>();
        addIfChanged(changed, "previousSchoolName", transfer.getPreviousSchoolName(), schoolName);
        addIfChanged(changed, "previousSchoolBoard", transfer.getPreviousSchoolBoard(), board);
        addIfChanged(changed, "transferCertificateNumber", transfer.getTransferCertificateNumber(), tcNumber);
        addIfChanged(
                changed,
                "transferCertificateIssuedOn",
                transfer.getTransferCertificateIssuedOn(),
                request.transferCertificateIssuedOn());
        addIfChanged(changed, "reasonForLeaving", transfer.getReasonForLeaving(), reason);
        if (changed.isEmpty()) {
            return PreviousSchoolDetail.of(transfer);
        }
        transfer.apply(schoolName, board, tcNumber, request.transferCertificateIssuedOn(), reason);
        transfers.saveAndFlush(transfer);
        audit.recordChange(AuditAction.ENTITY_UPDATED, StudentAudit.STUDENT_TRANSFER, studentId.toString(), changed);
        return PreviousSchoolDetail.of(transfer);
    }

    // ── Medical (FR-034) ─────────────────────────────────────────────────────────────────────

    /** The masked view: whether each Restricted field has been recorded, and the emergency contact in full. */
    public MedicalSummary medicalSummary(UUID studentId) {
        return medicals.findById(studentId).map(MedicalSummary::of).orElse(MedicalSummary.empty());
    }

    /**
     * The real values of the six Restricted fields. <strong>Every call is an audited read</strong>
     * (ADR-0014) — see {@link StudentAudit#RESTRICTED_DATA_REVEALED}. The audit row names whichever
     * of the six actually held a value for this student, the same "present fields only" rule
     * {@link #saveMedical} already uses for a first-time save: the row answers what this reveal
     * disclosed, not which fields the endpoint is shaped to return.
     */
    public MedicalDetail revealMedical(UUID studentId) {
        requireStudent(studentId);
        MedicalDetail detail =
                medicals.findById(studentId).map(MedicalDetail::of).orElse(MedicalDetail.empty());
        audit.recordSecurityEvent(
                StudentAudit.RESTRICTED_DATA_REVEALED,
                AuditOutcome.SUCCESS,
                StudentAudit.STUDENT_MEDICAL,
                studentId.toString(),
                presentFields(
                        "bloodGroup", detail.bloodGroup(),
                        "cwsnStatus", detail.cwsnStatus(),
                        "disabilityDetails", detail.disabilityDetails(),
                        "allergies", detail.allergies(),
                        "chronicConditions", detail.chronicConditions(),
                        "medication", detail.medication()));
        return detail;
    }

    @Transactional
    public MedicalSummary saveMedical(UUID studentId, SaveMedicalRequest request) {
        requireStudent(studentId);
        Optional<StudentMedical> existing = medicals.findById(studentId);

        String bloodGroup = blankToNull(request.bloodGroup());
        String cwsnStatus = blankToNull(request.cwsnStatus());
        String disabilityDetails = blankToNull(request.disabilityDetails());
        String allergies = blankToNull(request.allergies());
        String chronicConditions = blankToNull(request.chronicConditions());
        String medication = blankToNull(request.medication());
        String emergencyContactName = blankToNull(request.emergencyContactName());
        String emergencyContactPhone = blankToNull(request.emergencyContactPhone());
        String emergencyContactRelation = blankToNull(request.emergencyContactRelation());

        if (existing.isEmpty()) {
            if (bloodGroup == null
                    && cwsnStatus == null
                    && disabilityDetails == null
                    && allergies == null
                    && chronicConditions == null
                    && medication == null
                    && emergencyContactName == null
                    && emergencyContactPhone == null
                    && emergencyContactRelation == null) {
                return MedicalSummary.empty();
            }
            StudentMedical created = medicals.saveAndFlush(new StudentMedical(
                    studentId,
                    bloodGroup,
                    cwsnStatus,
                    disabilityDetails,
                    allergies,
                    chronicConditions,
                    medication,
                    emergencyContactName,
                    emergencyContactPhone,
                    emergencyContactRelation));
            audit.recordChange(
                    AuditAction.ENTITY_CREATED,
                    StudentAudit.STUDENT_MEDICAL,
                    studentId.toString(),
                    presentFields(
                            "bloodGroup", bloodGroup,
                            "cwsnStatus", cwsnStatus,
                            "disabilityDetails", disabilityDetails,
                            "allergies", allergies,
                            "chronicConditions", chronicConditions,
                            "medication", medication,
                            "emergencyContactName", emergencyContactName,
                            "emergencyContactPhone", emergencyContactPhone,
                            "emergencyContactRelation", emergencyContactRelation));
            return MedicalSummary.of(created);
        }

        StudentMedical medical = existing.get();
        Set<String> changed = new LinkedHashSet<>();
        addIfChanged(changed, "bloodGroup", medical.getBloodGroup(), bloodGroup);
        addIfChanged(changed, "cwsnStatus", medical.getCwsnStatus(), cwsnStatus);
        addIfChanged(changed, "disabilityDetails", medical.getDisabilityDetails(), disabilityDetails);
        addIfChanged(changed, "allergies", medical.getAllergies(), allergies);
        addIfChanged(changed, "chronicConditions", medical.getChronicConditions(), chronicConditions);
        addIfChanged(changed, "medication", medical.getMedication(), medication);
        addIfChanged(changed, "emergencyContactName", medical.getEmergencyContactName(), emergencyContactName);
        addIfChanged(changed, "emergencyContactPhone", medical.getEmergencyContactPhone(), emergencyContactPhone);
        addIfChanged(
                changed, "emergencyContactRelation", medical.getEmergencyContactRelation(), emergencyContactRelation);
        if (changed.isEmpty()) {
            return MedicalSummary.of(medical);
        }
        medical.apply(
                bloodGroup,
                cwsnStatus,
                disabilityDetails,
                allergies,
                chronicConditions,
                medication,
                emergencyContactName,
                emergencyContactPhone,
                emergencyContactRelation);
        medicals.saveAndFlush(medical);
        audit.recordChange(AuditAction.ENTITY_UPDATED, StudentAudit.STUDENT_MEDICAL, studentId.toString(), changed);
        return MedicalSummary.of(medical);
    }

    // ── Compliance and identifiers (FR-029) ─────────────────────────────────────────────────

    public ComplianceSummary complianceSummary(UUID studentId) {
        return compliances.findById(studentId).map(ComplianceSummary::of).orElse(ComplianceSummary.empty());
    }

    /**
     * The real values of the four Restricted fields. <strong>Every call is an audited read</strong>
     * (ADR-0014) — see {@link StudentAudit#RESTRICTED_DATA_REVEALED}. The audit row names whichever
     * of the four actually held a value for this student, the same "present fields only" rule
     * {@link #saveCompliance} already uses for a first-time save: the row answers what this reveal
     * disclosed, not which fields the endpoint is shaped to return.
     */
    public ComplianceDetail revealCompliance(UUID studentId) {
        requireStudent(studentId);
        ComplianceDetail detail =
                compliances.findById(studentId).map(ComplianceDetail::of).orElse(ComplianceDetail.empty());
        audit.recordSecurityEvent(
                StudentAudit.RESTRICTED_DATA_REVEALED,
                AuditOutcome.SUCCESS,
                StudentAudit.STUDENT_COMPLIANCE,
                studentId.toString(),
                presentFields(
                        "casteCategory", detail.casteCategory(),
                        "religion", detail.religion(),
                        "specialCategory", detail.specialCategory(),
                        "apaarId", detail.apaarId()));
        return detail;
    }

    @Transactional
    public ComplianceSummary saveCompliance(UUID studentId, SaveComplianceRequest request) {
        requireStudent(studentId);
        String apaarId = blankToNull(request.apaarId());
        if (apaarId != null && !request.apaarConsentGiven()) {
            // ADR-0014's consent section, and SaveComplianceRequest's own Javadoc: an id that is
            // only lawful with consent needs somewhere recording that consent was given, or it is
            // a liability rather than a field.
            throw new ChalkbaseException(StudentErrorCode.APAAR_REQUIRES_CONSENT);
        }

        Optional<StudentCompliance> existing = compliances.findById(studentId);
        String penUdiseId = blankToNull(request.penUdiseId());
        String boardRegistrationNumber = blankToNull(request.boardRegistrationNumber());
        String casteCategory = blankToNull(request.casteCategory());
        String religion = blankToNull(request.religion());
        String specialCategory = blankToNull(request.specialCategory());
        String apaarConsentGivenBy = blankToNull(request.apaarConsentGivenBy());

        if (existing.isEmpty()) {
            if (penUdiseId == null
                    && boardRegistrationNumber == null
                    && casteCategory == null
                    && religion == null
                    && specialCategory == null
                    && apaarId == null
                    && !request.apaarConsentGiven()) {
                return ComplianceSummary.empty();
            }
            Instant consentAt = request.apaarConsentGiven() ? Instant.now() : null;
            StudentCompliance created = compliances.saveAndFlush(new StudentCompliance(
                    studentId,
                    penUdiseId,
                    boardRegistrationNumber,
                    casteCategory,
                    religion,
                    specialCategory,
                    apaarId,
                    request.apaarConsentGiven(),
                    apaarConsentGivenBy,
                    consentAt));
            audit.recordChange(
                    AuditAction.ENTITY_CREATED,
                    StudentAudit.STUDENT_COMPLIANCE,
                    studentId.toString(),
                    presentFields(
                            "penUdiseId", penUdiseId,
                            "boardRegistrationNumber", boardRegistrationNumber,
                            "casteCategory", casteCategory,
                            "religion", religion,
                            "specialCategory", specialCategory,
                            "apaarId", apaarId,
                            "apaarConsentGiven", request.apaarConsentGiven() ? "true" : null,
                            "apaarConsentGivenBy", apaarConsentGivenBy));
            return ComplianceSummary.of(created);
        }

        StudentCompliance compliance = existing.get();
        // Consent, once given, is not withdrawn by a save that simply left the box unticked — a
        // school correcting a typo in the caste category should not silently erase a consent record
        // that a signed form backs. Only an explicit `false` where it was already `false` is a
        // no-op; turning `true` back to `false` is a real, audited change.
        boolean consentGiven = request.apaarConsentGiven() || compliance.isApaarConsentGiven();
        Instant consentAt = consentGiven
                ? (compliance.getApaarConsentGivenAt() == null ? Instant.now() : compliance.getApaarConsentGivenAt())
                : null;

        Set<String> changed = new LinkedHashSet<>();
        addIfChanged(changed, "penUdiseId", compliance.getPenUdiseId(), penUdiseId);
        addIfChanged(
                changed, "boardRegistrationNumber", compliance.getBoardRegistrationNumber(), boardRegistrationNumber);
        addIfChanged(changed, "casteCategory", compliance.getCasteCategory(), casteCategory);
        addIfChanged(changed, "religion", compliance.getReligion(), religion);
        addIfChanged(changed, "specialCategory", compliance.getSpecialCategory(), specialCategory);
        addIfChanged(changed, "apaarId", compliance.getApaarId(), apaarId);
        addIfChanged(changed, "apaarConsentGiven", compliance.isApaarConsentGiven(), consentGiven);
        addIfChanged(changed, "apaarConsentGivenBy", compliance.getApaarConsentGivenBy(), apaarConsentGivenBy);
        if (changed.isEmpty()) {
            return ComplianceSummary.of(compliance);
        }
        compliance.apply(
                penUdiseId,
                boardRegistrationNumber,
                casteCategory,
                religion,
                specialCategory,
                apaarId,
                consentGiven,
                apaarConsentGivenBy,
                consentAt);
        compliances.saveAndFlush(compliance);
        audit.recordChange(AuditAction.ENTITY_UPDATED, StudentAudit.STUDENT_COMPLIANCE, studentId.toString(), changed);
        return ComplianceSummary.of(compliance);
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    private void requireStudent(UUID id) {
        if (!students.existsById(id)) {
            throw new NotFoundException("Student", id);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void addIfChanged(Set<String> changed, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(field);
        }
    }

    /** For a first-time save: the names of every field that arrived with something in it. */
    private static Set<String> presentFields(Object... nameValuePairs) {
        Set<String> present = new LinkedHashSet<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            String name = (String) nameValuePairs[i];
            Object value = nameValuePairs[i + 1];
            if (value != null) {
                present.add(name);
            }
        }
        return present;
    }
}
