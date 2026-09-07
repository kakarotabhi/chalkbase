package in.chalkbase.student.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditOutcome;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.export.ClassificationCsvExporter;
import in.chalkbase.platform.export.ClassificationCsvExporter.Mode;
import in.chalkbase.student.api.CurrentEnrolment;
import in.chalkbase.student.api.StudentExportRow;
import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentAudit;
import in.chalkbase.student.domain.StudentCompliance;
import in.chalkbase.student.domain.StudentContact;
import in.chalkbase.student.domain.StudentGuardianLink;
import in.chalkbase.student.domain.StudentMedical;
import in.chalkbase.student.domain.StudentQuery;
import in.chalkbase.student.domain.StudentTransfer;
import in.chalkbase.student.infrastructure.StudentComplianceRepository;
import in.chalkbase.student.infrastructure.StudentContactRepository;
import in.chalkbase.student.infrastructure.StudentGuardianRepository;
import in.chalkbase.student.infrastructure.StudentMedicalRepository;
import in.chalkbase.student.infrastructure.StudentQueries;
import in.chalkbase.student.infrastructure.StudentRepository;
import in.chalkbase.student.infrastructure.StudentTransferRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The student roster, as a CSV file (ADR-0014, ADR-0027).
 *
 * <p><strong>Masking is not a decision this class makes.</strong> Both {@link #exportMasked} and
 * {@link #exportUnmasked} build the exact same {@link StudentExportRow} for every student, with the
 * real values in every field — what differs is which {@link Mode} they hand to
 * {@code ClassificationCsvExporter}, which is the one place that reads each column's
 * {@code @Classification} and decides whether it reaches the file. See that class's Javadoc for why
 * the columns are never a second, hand-written list here.
 *
 * <p><strong>Every export is audited, not only the unmasked one.</strong> ADR-0014 says a
 * Confidential export is audited unconditionally, and a masked file still contains a name and an
 * admission number — see {@code StudentAudit#STUDENT_EXPORT}. The audit call is the same for both
 * modes; only the field list it carries differs, because the exporter returns the columns it
 * actually wrote.
 *
 * <p><strong>Streaming, not buffering</strong> (see {@code ClassificationCsvExporter}). Loading the
 * matching {@link Student} rows and their related sections into memory as entities is not what this
 * class is careful about — a school of a few thousand students is a few thousand small objects, the
 * same order of magnitude {@code StudentService#list} already loads for one page's worth of related
 * data, just without the page limit. What it does not do is build the CSV's text anywhere before
 * writing it: {@link #export} streams each {@link StudentExportRow} straight into the exporter, one
 * row written and discarded before the next is even built, so the file's size never becomes a
 * second, larger allocation on top of the entities.
 */
@Service
@Transactional(readOnly = true)
public class StudentExportService {

    private final StudentRepository students;
    private final StudentContactRepository contacts;
    private final StudentTransferRepository transfers;
    private final StudentMedicalRepository medicals;
    private final StudentComplianceRepository compliances;
    private final StudentGuardianRepository guardianLinks;
    private final StudentService studentService;
    private final AcademicsLookup academics;
    private final AuditService audit;

    public StudentExportService(
            StudentRepository students,
            StudentContactRepository contacts,
            StudentTransferRepository transfers,
            StudentMedicalRepository medicals,
            StudentComplianceRepository compliances,
            StudentGuardianRepository guardianLinks,
            StudentService studentService,
            AcademicsLookup academics,
            AuditService audit) {
        this.students = students;
        this.contacts = contacts;
        this.transfers = transfers;
        this.medicals = medicals;
        this.compliances = compliances;
        this.guardianLinks = guardianLinks;
        this.studentService = studentService;
        this.academics = academics;
        this.audit = audit;
    }

    /**
     * ADR-0014's default. Every Restricted field — caste, religion, category, CWSN/disability,
     * health details, blood group, APAAR — is left out of the file entirely; everything else that
     * matches {@code query} is in it. Gated on {@code student:student:read}, the same permission
     * that reads the list and a single record — see {@code StudentController}.
     */
    public void exportMasked(StudentQuery query, OutputStream out) throws IOException {
        export(query, out, Mode.MASKED);
    }

    /**
     * Every field, Restricted included. The caller is responsible for having already checked
     * {@code student:student:export_unmasked} (see {@code StudentController}); this method's only
     * job past that is to build the file and to audit, unconditionally, that it did.
     */
    public void exportUnmasked(StudentQuery query, OutputStream out) throws IOException {
        export(query, out, Mode.UNMASKED);
    }

    private void export(StudentQuery query, OutputStream out, Mode mode) throws IOException {
        UUID currentSessionId =
                academics.currentSession().map(AcademicSessionRef::id).orElse(null);
        List<Student> matches = students.findAll(StudentQueries.matching(query, currentSessionId), Sort.by("fullName"));
        List<UUID> ids = matches.stream().map(Student::getId).toList();

        Map<UUID, CurrentEnrolment> placements = studentService.currentEnrolments(ids);
        Map<UUID, StudentContact> contactById = indexById(contacts.findAllById(ids), StudentContact::getStudentId);
        Map<UUID, StudentTransfer> transferById = indexById(transfers.findAllById(ids), StudentTransfer::getStudentId);
        Map<UUID, StudentMedical> medicalById = indexById(medicals.findAllById(ids), StudentMedical::getStudentId);
        Map<UUID, StudentCompliance> complianceById =
                indexById(compliances.findAllById(ids), StudentCompliance::getStudentId);
        Map<UUID, List<StudentGuardianLink>> guardiansByStudent = ids.isEmpty()
                ? Map.of()
                : guardianLinks.findByStudentIdInWithGuardian(ids).stream()
                        .collect(Collectors.groupingBy(
                                link -> link.getStudent().getId(), LinkedHashMap::new, Collectors.toList()));

        List<StudentExportRow> rows = matches.stream()
                .map(student -> rowOf(
                        student,
                        placements.get(student.getId()),
                        contactById.get(student.getId()),
                        transferById.get(student.getId()),
                        medicalById.get(student.getId()),
                        complianceById.get(student.getId()),
                        guardiansByStudent.getOrDefault(student.getId(), List.of())))
                .toList();

        List<String> fields = ClassificationCsvExporter.write(out, StudentExportRow.class, rows.stream(), mode);

        // ADR-0014: a Confidential export is audited, masked or not — see the class Javadoc. Its
        // own transaction (ADR-0018 §4), so this row survives even if the response does not, the
        // same as StudentAudit#RESTRICTED_DATA_REVEALED.
        audit.recordSecurityEvent(
                AuditAction.DATA_EXPORTED,
                AuditOutcome.SUCCESS,
                StudentAudit.STUDENT_EXPORT,
                null,
                fields,
                rows.size());
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    private static StudentExportRow rowOf(
            Student student,
            CurrentEnrolment enrolment,
            StudentContact contact,
            StudentTransfer transfer,
            StudentMedical medical,
            StudentCompliance compliance,
            List<StudentGuardianLink> guardians) {
        return new StudentExportRow(
                student.getId(),
                student.getAdmissionNumber(),
                student.getFullName(),
                student.getGender(),
                student.getStatus(),
                student.getDateOfBirth(),
                student.getAdmittedOn(),
                enrolment == null ? null : enrolment.sessionName(),
                enrolment == null ? null : enrolment.className(),
                enrolment == null ? null : enrolment.sectionName(),
                enrolment == null ? null : enrolment.rollNumber(),
                contact == null ? null : contact.getAddress(),
                contact == null ? null : contact.getPhone(),
                contact == null ? null : contact.getEmail(),
                guardianNamesOf(guardians),
                guardianPhonesOf(guardians),
                transfer == null ? null : transfer.getPreviousSchoolName(),
                transfer == null ? null : transfer.getPreviousSchoolBoard(),
                transfer == null ? null : transfer.getTransferCertificateNumber(),
                transfer == null ? null : transfer.getTransferCertificateIssuedOn(),
                medical == null ? null : medical.getEmergencyContactName(),
                medical == null ? null : medical.getEmergencyContactPhone(),
                medical == null ? null : medical.getEmergencyContactRelation(),
                medical == null ? null : medical.getBloodGroup(),
                medical == null ? null : medical.getCwsnStatus(),
                medical == null ? null : medical.getDisabilityDetails(),
                medical == null ? null : medical.getAllergies(),
                medical == null ? null : medical.getChronicConditions(),
                medical == null ? null : medical.getMedication(),
                compliance == null ? null : compliance.getPenUdiseId(),
                compliance == null ? null : compliance.getBoardRegistrationNumber(),
                compliance != null && compliance.isApaarConsentGiven(),
                compliance == null ? null : compliance.getApaarConsentGivenBy(),
                compliance == null ? null : compliance.getCasteCategory(),
                compliance == null ? null : compliance.getReligion(),
                compliance == null ? null : compliance.getSpecialCategory(),
                compliance == null ? null : compliance.getApaarId());
    }

    /** Primary contact first — {@code findByStudentIdInWithGuardian} already orders it that way. */
    private static String guardianNamesOf(List<StudentGuardianLink> guardians) {
        return guardians.stream()
                .map(link -> link.getGuardian().getFullName() + " ("
                        + link.getRelation().name() + ")")
                .collect(Collectors.joining("; "));
    }

    private static String guardianPhonesOf(List<StudentGuardianLink> guardians) {
        return guardians.stream()
                .map(link -> link.getGuardian().getPhone())
                .filter(phone -> phone != null && !phone.isBlank())
                .collect(Collectors.joining("; "));
    }

    private static <T> Map<UUID, T> indexById(List<T> entities, Function<T, UUID> idOf) {
        Map<UUID, T> byId = new LinkedHashMap<>();
        for (T entity : entities) {
            byId.put(idOf.apply(entity), entity);
        }
        return byId;
    }
}
