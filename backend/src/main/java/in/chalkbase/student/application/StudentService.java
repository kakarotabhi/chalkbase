package in.chalkbase.student.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.student.api.CreateEnrolmentRequest;
import in.chalkbase.student.api.CurrentEnrolment;
import in.chalkbase.student.api.Enrolment;
import in.chalkbase.student.api.SaveStudentRequest;
import in.chalkbase.student.api.StudentDetail;
import in.chalkbase.student.api.StudentGuardian;
import in.chalkbase.student.api.StudentSummary;
import in.chalkbase.student.api.UpdateEnrolmentRequest;
import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentAudit;
import in.chalkbase.student.domain.StudentEnrolment;
import in.chalkbase.student.domain.StudentErrorCode;
import in.chalkbase.student.domain.StudentQuery;
import in.chalkbase.student.infrastructure.StudentEnrolmentRepository;
import in.chalkbase.student.infrastructure.StudentGuardianRepository;
import in.chalkbase.student.infrastructure.StudentQueries;
import in.chalkbase.student.infrastructure.StudentRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The children on this school's rolls, and where each of them sits.
 *
 * <p>Scoped to the school bound to this request and to no other: the connection's
 * {@code search_path} selects the schema (ADR-0011), so there is no school id to pass and none to
 * get wrong. An id belonging to another school is not in this schema, which makes it a 404 rather
 * than a leak.
 *
 * <p><strong>Nothing here deletes a student, and nothing ever will</strong> (ADR-0020 §6). Fees,
 * attendance and marks reference these rows and a school is legally required to produce them years
 * later; a child who leaves is {@code WITHDRAWN} or {@code TRANSFERRED}. Erasure under the DPDP Act
 * is a different operation with its own design.
 *
 * <p><strong>Audited per ADR-0018 and AGENTS rule 11</strong>: every write records the NAMES of the
 * fields it changed, in the same transaction as the change, and records nothing when nothing
 * differed. The {@code entityId} is always the student's UUID — never the admission number, never
 * the name, both of which are Confidential and identify a child (see {@code StudentAudit}).
 *
 * <p>Every field this class handles is Confidential under ADR-0014. Nothing here logs one, and no
 * exception message contains one: {@code StudentErrorCode} carries sentences with no values in
 * them, and a {@code NotFoundException} names the resource and the UUID the caller itself supplied.
 */
@Service
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository students;
    private final StudentEnrolmentRepository enrolments;
    private final StudentGuardianRepository links;
    private final AcademicsLookup academics;
    private final AuditService audit;

    public StudentService(
            StudentRepository students,
            StudentEnrolmentRepository enrolments,
            StudentGuardianRepository links,
            AcademicsLookup academics,
            AuditService audit) {
        this.students = students;
        this.enrolments = enrolments;
        this.links = links;
        this.academics = academics;
        this.audit = audit;
    }

    // ── Students ─────────────────────────────────────────────────────────────────────────────

    /**
     * One page of the student list, with each student's current placement.
     *
     * <p>Paged, unlike the class ladder and the session list, because a school has one of these per
     * child rather than one per year: eight hundred rows is the ordinary case and forty thousand is
     * not far-fetched for a group's oldest campus.
     *
     * <p>Three queries whatever the page size — the students, their live enrolments, and the
     * sections those name — rather than two per row. The section names come from {@code academics}
     * in one batch through its named interface; a join would cross a module boundary the module map
     * forbids.
     */
    public PageResponse<StudentSummary> list(StudentQuery query, Pageable pageable) {
        UUID currentSessionId =
                academics.currentSession().map(AcademicSessionRef::id).orElse(null);

        Page<Student> page = students.findAll(StudentQueries.matching(query, currentSessionId), pageable);
        Map<UUID, CurrentEnrolment> placements =
                currentEnrolments(page.getContent().stream().map(Student::getId).toList(), currentSessionId);

        List<StudentSummary> content = page.getContent().stream()
                .map(student -> StudentSummary.of(student, placements.get(student.getId())))
                .toList();
        return PageResponse.of(page, content);
    }

    /** One child's whole record: their guardians, and every placement they have ever had. */
    public StudentDetail find(UUID id) {
        Student student = require(id);
        return detailOf(student);
    }

    /**
     * A child admitted.
     *
     * <p>Created with no enrolment and no guardians, deliberately — see {@code SaveStudentRequest}.
     * Placing a child in a section and attaching a parent are separate decisions the office makes at
     * separate moments, and requiring them here would make a student unrecordable until both had
     * been made.
     */
    @Transactional
    public StudentDetail create(SaveStudentRequest request) {
        Student student = students.saveAndFlush(new Student(
                request.admissionNumber().trim(),
                request.fullName().trim(),
                request.dateOfBirth(),
                request.gender(),
                request.status(),
                request.admittedOn()));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                StudentAudit.STUDENT,
                student.getId().toString(),
                List.of("admissionNumber", "fullName", "dateOfBirth", "gender", "status", "admittedOn"));

        return detailOf(student);
    }

    /** Corrects a child's record. Never deletes one; {@code status} is what leaving looks like. */
    @Transactional
    public StudentDetail update(UUID id, SaveStudentRequest request) {
        Student student = require(id);
        String admissionNumber = request.admissionNumber().trim();
        String fullName = request.fullName().trim();

        // Diffed before the entity is mutated, because afterwards there is nothing to compare
        // against — the same order AcademicSessionService and SchoolProfileService use.
        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(student.getAdmissionNumber(), admissionNumber)) {
            changed.add("admissionNumber");
        }
        if (!Objects.equals(student.getFullName(), fullName)) {
            changed.add("fullName");
        }
        if (!Objects.equals(student.getDateOfBirth(), request.dateOfBirth())) {
            changed.add("dateOfBirth");
        }
        if (student.getGender() != request.gender()) {
            changed.add("gender");
        }
        if (student.getStatus() != request.status()) {
            changed.add("status");
        }
        if (!Objects.equals(student.getAdmittedOn(), request.admittedOn())) {
            changed.add("admittedOn");
        }
        if (changed.isEmpty()) {
            // A form resubmitted unchanged is the commonest way to fill an audit log with rows that
            // say nothing. Nothing is written, and nothing is recorded.
            return detailOf(student);
        }

        student.apply(
                admissionNumber,
                fullName,
                request.dateOfBirth(),
                request.gender(),
                request.status(),
                request.admittedOn());
        students.saveAndFlush(student);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                StudentAudit.STUDENT,
                student.getId().toString(),
                changed);

        return detailOf(student);
    }

    // ── Enrolment ────────────────────────────────────────────────────────────────────────────

    /**
     * Places a student in a section for an academic year.
     *
     * <p>The second active enrolment in one year is refused <em>here</em> as well as by
     * {@code uq_student_enrolment_one_active}, and the two are not redundant. The check gives the
     * office a sentence it can act on — end the previous placement — where the index alone gives a
     * conflict from a name nobody outside this file has seen. The index is what holds when two
     * clerks press Save at the same instant, which the check cannot.
     *
     * <p>The session and the section are resolved through {@code academics} before anything is
     * written, so an id from another school is refused as an unprocessable body rather than as a
     * foreign key violation the school would read as "something went wrong".
     */
    @Transactional
    public Enrolment enrol(UUID studentId, CreateEnrolmentRequest request) {
        Student student = require(studentId);
        AcademicSessionRef session = requireSession(request.academicSessionId());
        SectionRef section = requireSection(request.sectionId());

        enrolments
                .findFirstByStudentIdAndAcademicSessionIdAndActiveTrue(studentId, session.id())
                .ifPresent(existing -> {
                    throw new ChalkbaseException(StudentErrorCode.ALREADY_ENROLLED_THIS_SESSION);
                });

        StudentEnrolment enrolment = enrolments.saveAndFlush(
                new StudentEnrolment(student, session.id(), section.id(), trimmedOrNull(request.rollNumber())));

        // Against the STUDENT's id, not the enrolment's: "what happened to this child" is the
        // question the log will be asked, and it cannot be answered by an id nobody has (StudentAudit).
        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                StudentAudit.STUDENT_ENROLMENT,
                student.getId().toString(),
                List.of("academicSessionId", "sectionId", "rollNumber"));

        return Enrolment.of(enrolment, session, section);
    }

    /**
     * Moves a placement to another section, renumbers it, ends it, or brings it back.
     *
     * <p>The academic year is not editable — see {@code UpdateEnrolmentRequest}. Reactivating a
     * placement is checked against the same one-active-per-year rule as creating one, because a
     * child brought back into last term's section while enrolled in this term's would be in two
     * sections at once and the index would refuse the write anyway.
     */
    @Transactional
    public Enrolment updateEnrolment(UUID studentId, UUID enrolmentId, UpdateEnrolmentRequest request) {
        require(studentId);
        StudentEnrolment enrolment = enrolments
                .findByIdAndStudentId(enrolmentId, studentId)
                .orElseThrow(() -> new NotFoundException("Enrolment", enrolmentId));
        SectionRef section = requireSection(request.sectionId());
        String rollNumber = trimmedOrNull(request.rollNumber());

        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(enrolment.getSectionId(), section.id())) {
            changed.add("sectionId");
        }
        if (!Objects.equals(enrolment.getRollNumber(), rollNumber)) {
            changed.add("rollNumber");
        }
        if (enrolment.isActive() != request.active()) {
            changed.add("active");
        }
        if (changed.isEmpty()) {
            return Enrolment.of(enrolment, sessionOf(enrolment), section);
        }

        if (request.active() && !enrolment.isActive()) {
            enrolments
                    .findFirstByStudentIdAndAcademicSessionIdAndActiveTrue(studentId, enrolment.getAcademicSessionId())
                    .ifPresent(other -> {
                        throw new ChalkbaseException(StudentErrorCode.ALREADY_ENROLLED_THIS_SESSION);
                    });
        }

        enrolment.moveTo(section.id());
        enrolment.setRollNumber(rollNumber);
        enrolment.setActive(request.active());
        enrolments.saveAndFlush(enrolment);

        audit.recordChange(AuditAction.ENTITY_UPDATED, StudentAudit.STUDENT_ENROLMENT, studentId.toString(), changed);

        return Enrolment.of(enrolment, sessionOf(enrolment), section);
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    /** Assembled here rather than in the controller, so every write answers with the same whole record. */
    StudentDetail detailOf(Student student) {
        List<StudentEnrolment> history = enrolments.findByStudentIdOrderByEnrolledOnDescCreatedAtDesc(student.getId());
        Map<UUID, AcademicSessionRef> sessions = academics.sessions(
                history.stream().map(StudentEnrolment::getAcademicSessionId).toList());
        Map<UUID, SectionRef> sections = academics.sections(
                history.stream().map(StudentEnrolment::getSectionId).toList());

        List<Enrolment> placements = history.stream()
                .map(enrolment -> Enrolment.of(
                        enrolment,
                        sessions.get(enrolment.getAcademicSessionId()),
                        sections.get(enrolment.getSectionId())))
                // Newest year first, by the session's own start date now that it has been resolved.
                // A student promoted in April and corrected in June has two rows whose enrolledOn
                // dates say nothing useful about which year each belongs to.
                .sorted(Comparator.comparing(
                                (Enrolment placement) -> startOf(sessions, placement.sessionId()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Enrolment::enrolledOn, Comparator.reverseOrder()))
                .toList();

        List<StudentGuardian> guardians = links.findByStudentIdWithGuardian(student.getId()).stream()
                .map(StudentGuardian::of)
                .toList();

        UUID currentSessionId =
                academics.currentSession().map(AcademicSessionRef::id).orElse(null);
        CurrentEnrolment current = history.stream()
                .filter(enrolment ->
                        enrolment.isActive() && Objects.equals(enrolment.getAcademicSessionId(), currentSessionId))
                .findFirst()
                .map(enrolment -> currentEnrolmentOf(
                        enrolment,
                        sessions.get(enrolment.getAcademicSessionId()),
                        sections.get(enrolment.getSectionId())))
                .orElse(null);

        return StudentDetail.of(student, current, guardians, placements);
    }

    /**
     * Where each of these students sits <strong>this year</strong>.
     *
     * <p>"This year" is the session the school has flagged current, not the most recent one it has
     * created. A school setting up 2027-28 in February would otherwise see next year's class against
     * every child on the list while still teaching 2026-27, with nothing on the row to say the
     * number was for a different year — a wrong answer that looks like a right one.
     *
     * <p>The consequence is stated rather than worked around: a school that has never declared a
     * current session sees no current enrolment anywhere, and the fix is one click on the sessions
     * screen. Guessing on its behalf would be the wrong kind of helpful.
     */
    private Map<UUID, CurrentEnrolment> currentEnrolments(List<UUID> studentIds, UUID currentSessionId) {
        if (studentIds.isEmpty() || currentSessionId == null) {
            return Map.of();
        }
        List<StudentEnrolment> live =
                enrolments.findByActiveTrueAndAcademicSessionIdAndStudentIdIn(currentSessionId, studentIds);
        if (live.isEmpty()) {
            return Map.of();
        }

        AcademicSessionRef session = academics.session(currentSessionId).orElse(null);
        Map<UUID, SectionRef> sections = academics.sections(
                live.stream().map(StudentEnrolment::getSectionId).toList());

        Map<UUID, CurrentEnrolment> byStudent = new LinkedHashMap<>();
        for (StudentEnrolment enrolment : live) {
            byStudent.put(
                    enrolment.getStudent().getId(),
                    currentEnrolmentOf(enrolment, session, sections.get(enrolment.getSectionId())));
        }
        return byStudent;
    }

    private static CurrentEnrolment currentEnrolmentOf(
            StudentEnrolment enrolment, AcademicSessionRef session, SectionRef section) {
        return new CurrentEnrolment(
                session == null ? null : session.name(),
                section == null ? null : section.className(),
                section == null ? null : section.name(),
                enrolment.getRollNumber());
    }

    private static java.time.LocalDate startOf(Map<UUID, AcademicSessionRef> sessions, UUID sessionId) {
        AcademicSessionRef session = sessions.get(sessionId);
        return session == null ? null : session.startsOn();
    }

    private AcademicSessionRef sessionOf(StudentEnrolment enrolment) {
        return academics.session(enrolment.getAcademicSessionId()).orElse(null);
    }

    private Student require(UUID id) {
        return students.findById(id).orElseThrow(() -> new NotFoundException("Student", id));
    }

    /**
     * Refuses a session that is not this school's, as an unprocessable body rather than a 404.
     *
     * <p>The student in the path does exist; it is the body that named something unreachable. A 404
     * here would tell a client its screen was showing a child who had been removed, and a screen
     * that reacted by clearing itself would look, to the office, like a student had just vanished.
     */
    private AcademicSessionRef requireSession(UUID sessionId) {
        return academics
                .session(sessionId)
                .orElseThrow(() -> new ChalkbaseException(StudentErrorCode.UNKNOWN_ACADEMIC_SESSION));
    }

    private SectionRef requireSection(UUID sectionId) {
        return academics.section(sectionId).orElseThrow(() -> new ChalkbaseException(StudentErrorCode.UNKNOWN_SECTION));
    }

    /**
     * A blank roll number is no roll number.
     *
     * <p>An empty string from a cleared form would otherwise be a value, and
     * {@code uq_student_enrolment_roll} would let exactly one student per section hold it — so the
     * second child whose roll number was cleared would be refused for a reason nobody could explain.
     * Null is what "not assigned yet" means, and nulls do not collide in a unique index.
     */
    private static String trimmedOrNull(String rollNumber) {
        if (rollNumber == null) {
            return null;
        }
        String trimmed = rollNumber.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
