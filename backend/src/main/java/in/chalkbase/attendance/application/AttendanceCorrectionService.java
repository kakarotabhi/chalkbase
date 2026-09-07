package in.chalkbase.attendance.application;

import in.chalkbase.attendance.api.CorrectionRequestResponse;
import in.chalkbase.attendance.api.DecideCorrectionRequest;
import in.chalkbase.attendance.domain.AttendanceAudit;
import in.chalkbase.attendance.domain.AttendanceCorrectionRequest;
import in.chalkbase.attendance.domain.AttendanceErrorCode;
import in.chalkbase.attendance.domain.AttendanceMark;
import in.chalkbase.attendance.domain.CorrectionDecision;
import in.chalkbase.attendance.infrastructure.AttendanceCorrectionRequestRepository;
import in.chalkbase.attendance.infrastructure.AttendanceMarkRepository;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.CurrentUser;
import in.chalkbase.student.api.StudentLookup;
import in.chalkbase.student.api.StudentNameRef;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An administrator's side of a correction request: the queue, and the decision.
 *
 * <p>Separate from {@link AttendanceMarkingService}: that one is the marking screen's read and
 * write model, and this is the only place {@code attendance:correction:approve} applies. Keeping
 * them apart means a change to the marking screen cannot quietly change what an approver can do.
 *
 * <p>Every row this service resolves a mark for assumes the mark exists — {@code fk_attendance_correction_mark}
 * makes that a database guarantee, not an assumption this class has to defend.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceCorrectionService {

    private final AttendanceCorrectionRequestRepository corrections;
    private final AttendanceMarkRepository marks;
    private final StudentLookup students;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AttendanceCorrectionService(
            AttendanceCorrectionRequestRepository corrections,
            AttendanceMarkRepository marks,
            StudentLookup students,
            AuditService audit,
            CurrentUser currentUser) {
        this.corrections = corrections;
        this.marks = marks;
        this.students = students;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** Requests awaiting a decision, oldest first — the admin's own queue. */
    public PageResponse<CorrectionRequestResponse> pendingQueue(Pageable pageable) {
        Page<AttendanceCorrectionRequest> page =
                corrections.findByDecisionOrderByRequestedAtAsc(CorrectionDecision.PENDING, pageable);
        return PageResponse.of(page, toResponses(page.getContent()));
    }

    /**
     * Approves or rejects a request. An approval writes the new status onto the mark it targets, in
     * the same transaction as the decision — both the original mark's creation and this application
     * are separate audit entries against the same mark id, per
     * {@link AttendanceAudit#CORRECTION_APPLIED}.
     */
    @Transactional
    public CorrectionRequestResponse decide(UUID requestId, DecideCorrectionRequest decision) {
        if (decision.decision() == CorrectionDecision.PENDING) {
            throw new ChalkbaseException(AttendanceErrorCode.CORRECTION_NOT_PENDING);
        }
        AttendanceCorrectionRequest request = corrections
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("Correction request", requestId));

        request.decide(decision.decision(), currentUser.require(), decision.note());
        corrections.saveAndFlush(request);

        AttendanceMark mark = marks.findById(request.getAttendanceMarkId())
                .orElseThrow(() -> new NotFoundException("Attendance mark", request.getAttendanceMarkId()));

        if (decision.decision() == CorrectionDecision.APPROVED) {
            mark.applyCorrection(request.getRequestedStatus());
            audit.recordChange(
                    AttendanceAudit.CORRECTION_APPLIED,
                    AttendanceAudit.ATTENDANCE_MARK,
                    mark.getId().toString(),
                    List.of("status"));
        }

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                AttendanceAudit.CORRECTION_REQUEST,
                request.getId().toString(),
                List.of("decision", "decisionNote"));

        return toResponse(request, mark);
    }

    private List<CorrectionRequestResponse> toResponses(Collection<AttendanceCorrectionRequest> requests) {
        List<UUID> markIds = requests.stream()
                .map(AttendanceCorrectionRequest::getAttendanceMarkId)
                .toList();
        Map<UUID, AttendanceMark> marksById =
                marks.findAllById(markIds).stream().collect(Collectors.toMap(AttendanceMark::getId, mark -> mark));
        Set<UUID> studentIds =
                marksById.values().stream().map(AttendanceMark::getStudentId).collect(Collectors.toSet());
        Map<UUID, StudentNameRef> names = students.namesOf(studentIds);

        return requests.stream()
                .map(request -> {
                    AttendanceMark mark = marksById.get(request.getAttendanceMarkId());
                    return CorrectionRequestResponse.of(
                            request, mark.getStudentId(), nameOf(names, mark.getStudentId()), mark.getAttendanceDate());
                })
                .toList();
    }

    private CorrectionRequestResponse toResponse(AttendanceCorrectionRequest request, AttendanceMark mark) {
        Map<UUID, StudentNameRef> names = students.namesOf(Set.of(mark.getStudentId()));
        return CorrectionRequestResponse.of(
                request, mark.getStudentId(), nameOf(names, mark.getStudentId()), mark.getAttendanceDate());
    }

    private static String nameOf(Map<UUID, StudentNameRef> names, UUID studentId) {
        StudentNameRef ref = names.get(studentId);
        return ref != null ? ref.fullName() : "Unknown student";
    }
}
