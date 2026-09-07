package in.chalkbase.attendance.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.attendance.api.AttendanceEntryRequest;
import in.chalkbase.attendance.api.AttendanceStudentMark;
import in.chalkbase.attendance.api.CorrectionRequestResponse;
import in.chalkbase.attendance.api.MarkAttendanceRequest;
import in.chalkbase.attendance.api.RequestCorrectionRequest;
import in.chalkbase.attendance.api.SectionAttendanceView;
import in.chalkbase.attendance.api.StudentAttendanceRecord;
import in.chalkbase.attendance.domain.AttendanceAudit;
import in.chalkbase.attendance.domain.AttendanceCorrectionRequest;
import in.chalkbase.attendance.domain.AttendanceErrorCode;
import in.chalkbase.attendance.domain.AttendanceMark;
import in.chalkbase.attendance.domain.CorrectionDecision;
import in.chalkbase.attendance.infrastructure.AttendanceCorrectionRequestRepository;
import in.chalkbase.attendance.infrastructure.AttendanceMarkRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.CurrentUser;
import in.chalkbase.student.api.EnrolledStudentRef;
import in.chalkbase.student.api.StudentLookup;
import in.chalkbase.student.api.StudentNameRef;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marking, viewing and correcting daily attendance for a section (Phase 0 decision 8, ADR-0030).
 *
 * <p>Reaches {@code academics} and {@code student} only through their named interfaces —
 * {@link AcademicsLookup} and {@link StudentLookup} — never through a join or a domain import.
 *
 * <p>The school's <strong>current</strong> academic session is resolved here, never accepted from
 * the client: see {@link MarkAttendanceRequest}'s own Javadoc for why.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceMarkingService {

    /** What {@link #mark} tells the audit log changed. Always both: this is a bulk upsert, not a diff. */
    private static final List<String> MARK_CHANGED_FIELDS = List.of("status", "remarks");

    private final AttendanceMarkRepository marks;
    private final AttendanceCorrectionRequestRepository corrections;
    private final AcademicsLookup academics;
    private final StudentLookup students;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AttendanceMarkingService(
            AttendanceMarkRepository marks,
            AttendanceCorrectionRequestRepository corrections,
            AcademicsLookup academics,
            StudentLookup students,
            AuditService audit,
            CurrentUser currentUser) {
        this.marks = marks;
        this.corrections = corrections;
        this.academics = academics;
        this.students = students;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** A section's roster for {@code date}, each student's mark if one exists yet. */
    public SectionAttendanceView view(UUID sectionId, LocalDate date) {
        SectionRef section = requireSection(sectionId);
        AcademicSessionRef session = requireCurrentSession();
        return buildView(section, session, date, isEditable(date));
    }

    /**
     * Marks or edits a section's attendance for one date, upserting every entry sent in one
     * transaction.
     *
     * <p>A student not on the section's live roster is refused (
     * {@link AttendanceErrorCode#STUDENT_NOT_ENROLLED_IN_SECTION}) rather than written anyway — the
     * roster the screen showed may have gone stale between load and save. A date that has already
     * locked is refused wholesale ({@link AttendanceErrorCode#MARK_LOCKED}): the client should be
     * offering a correction request instead, and a partial write that silently dropped the locked
     * entries would be worse than refusing the whole call.
     */
    @Transactional
    public SectionAttendanceView mark(UUID sectionId, MarkAttendanceRequest request) {
        SectionRef section = requireSection(sectionId);
        AcademicSessionRef session = requireCurrentSession();
        LocalDate date = request.attendanceDate();

        if (!isEditable(date)) {
            throw new ChalkbaseException(AttendanceErrorCode.MARK_LOCKED);
        }

        Set<UUID> roster = students.rosterOfSection(sectionId, session.id()).stream()
                .map(EnrolledStudentRef::studentId)
                .collect(Collectors.toUnmodifiableSet());

        List<UUID> studentIds = request.entries().stream()
                .map(AttendanceEntryRequest::studentId)
                .toList();
        Map<UUID, AttendanceMark> existingByStudent =
                marks.findByAttendanceDateAndPeriodNumberIsNullAndStudentIdIn(date, studentIds).stream()
                        .collect(Collectors.toMap(AttendanceMark::getStudentId, mark -> mark));

        UUID actingUser = currentUser.require();
        int touched = 0;
        for (AttendanceEntryRequest entry : request.entries()) {
            if (!roster.contains(entry.studentId())) {
                throw new ChalkbaseException(
                        AttendanceErrorCode.STUDENT_NOT_ENROLLED_IN_SECTION,
                        AttendanceErrorCode.STUDENT_NOT_ENROLLED_IN_SECTION.defaultMessage(),
                        Map.of("studentId", entry.studentId().toString()));
            }
            AttendanceMark existing = existingByStudent.get(entry.studentId());
            if (existing == null) {
                marks.save(new AttendanceMark(
                        entry.studentId(), session.id(), sectionId, date, entry.status(), entry.remarks(), actingUser));
            } else {
                existing.mark(entry.status(), entry.remarks(), actingUser);
            }
            touched++;
        }

        audit.recordBulkChange(
                AttendanceAudit.MARKS_RECORDED,
                AttendanceAudit.ATTENDANCE_MARK,
                sectionId + "@" + date,
                MARK_CHANGED_FIELDS,
                touched);

        return buildView(section, session, date, true);
    }

    /** One student's daily attendance between two dates, inclusive, oldest first. */
    public List<StudentAttendanceRecord> studentHistory(UUID studentId, LocalDate from, LocalDate to) {
        return marks
                .findByStudentIdAndPeriodNumberIsNullAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        studentId, from, to)
                .stream()
                .map(mark -> StudentAttendanceRecord.of(mark, isEditable(mark.getAttendanceDate())))
                .toList();
    }

    /** Every correction request ever filed against one mark, newest first — a teacher checking their own history. */
    public List<CorrectionRequestResponse> correctionHistory(UUID markId) {
        AttendanceMark mark = requireMark(markId);
        return corrections.findByAttendanceMarkIdOrderByRequestedAtDesc(markId).stream()
                .map(request -> toResponse(request, mark.getStudentId(), mark.getAttendanceDate()))
                .toList();
    }

    /**
     * Files a correction request against a locked mark.
     *
     * <p>Refused if the mark can still be edited directly ({@link AttendanceErrorCode#CORRECTION_NOT_ALLOWED_YET})
     * or already has a request awaiting a decision ({@link AttendanceErrorCode#CORRECTION_ALREADY_PENDING},
     * also enforced by {@code uq_attendance_correction_one_pending} for a concurrent second attempt).
     */
    @Transactional
    public CorrectionRequestResponse requestCorrection(UUID markId, RequestCorrectionRequest request) {
        AttendanceMark mark = requireMark(markId);
        if (isEditable(mark.getAttendanceDate())) {
            throw new ChalkbaseException(AttendanceErrorCode.CORRECTION_NOT_ALLOWED_YET);
        }
        if (corrections
                .findByAttendanceMarkIdAndDecision(markId, CorrectionDecision.PENDING)
                .isPresent()) {
            throw new ChalkbaseException(AttendanceErrorCode.CORRECTION_ALREADY_PENDING);
        }

        AttendanceCorrectionRequest created = new AttendanceCorrectionRequest(
                markId, mark.getStatus(), request.requestedStatus(), request.reason(), currentUser.require());
        corrections.saveAndFlush(created);

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AttendanceAudit.CORRECTION_REQUEST,
                created.getId().toString(),
                List.of("requestedStatus", "reason"));

        return toResponse(created, mark.getStudentId(), mark.getAttendanceDate());
    }

    private SectionAttendanceView buildView(
            SectionRef section, AcademicSessionRef session, LocalDate date, boolean editable) {
        List<EnrolledStudentRef> roster = students.rosterOfSection(section.id(), session.id());
        Map<UUID, AttendanceMark> existingByStudent =
                marks.findBySectionIdAndAttendanceDateAndPeriodNumberIsNull(section.id(), date).stream()
                        .collect(Collectors.toMap(AttendanceMark::getStudentId, mark -> mark));

        List<AttendanceStudentMark> entries = roster.stream()
                .map(student -> {
                    AttendanceMark mark = existingByStudent.get(student.studentId());
                    return mark == null
                            ? AttendanceStudentMark.unmarked(student, editable)
                            : AttendanceStudentMark.of(student, mark, editable);
                })
                .toList();

        return new SectionAttendanceView(
                section.id(), section.name(), section.className(), session.id(), date, !editable, entries);
    }

    private CorrectionRequestResponse toResponse(
            AttendanceCorrectionRequest request, UUID studentId, LocalDate attendanceDate) {
        Map<UUID, StudentNameRef> names = students.namesOf(Set.of(studentId));
        String studentName = names.containsKey(studentId) ? names.get(studentId).fullName() : "Unknown student";
        return CorrectionRequestResponse.of(request, studentId, studentName, attendanceDate);
    }

    /** Mirrors {@link AttendanceMark#isEditableOn} exactly, for a date with no mark yet to ask it of. */
    private static boolean isEditable(LocalDate attendanceDate) {
        return !LocalDate.now().isAfter(attendanceDate.plusDays(1));
    }

    private SectionRef requireSection(UUID sectionId) {
        return academics.section(sectionId).orElseThrow(() -> new NotFoundException("Section", sectionId));
    }

    private AcademicSessionRef requireCurrentSession() {
        return academics
                .currentSession()
                .orElseThrow(() -> new ChalkbaseException(AttendanceErrorCode.NO_CURRENT_SESSION));
    }

    private AttendanceMark requireMark(UUID markId) {
        return marks.findById(markId).orElseThrow(() -> new NotFoundException("Attendance mark", markId));
    }
}
