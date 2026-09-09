package in.chalkbase.attendance.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.attendance.api.CreateLeaveRequest;
import in.chalkbase.attendance.api.DecideLeaveRequest;
import in.chalkbase.attendance.api.LeaveRequestResponse;
import in.chalkbase.attendance.domain.AttendanceAudit;
import in.chalkbase.attendance.domain.AttendanceErrorCode;
import in.chalkbase.attendance.domain.AttendanceLeaveRequest;
import in.chalkbase.attendance.domain.LeaveDecision;
import in.chalkbase.attendance.infrastructure.AttendanceLeaveRequestRepository;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.CurrentUser;
import in.chalkbase.student.api.EnrolledStudentRef;
import in.chalkbase.student.api.StudentLookup;
import in.chalkbase.student.api.StudentNameRef;
import java.time.LocalDate;
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
 * Filing and deciding leave requests — a guardian-or-teacher's advance notice that a student will
 * be away, and an authorised person's decision on it (FR-047).
 *
 * <p>Separate from {@link AttendanceMarkingService} even though {@code create} validates a roster
 * the same way {@code mark} does: this service never touches {@code attendance_mark}, and that
 * absence is the point — see the ADR-0030 amendment. {@code AttendanceMarkingService.buildView}
 * reads {@link AttendanceLeaveRequestRepository} directly, one module-internal read, rather than
 * this service depending back on that one.
 *
 * <p>Reaches {@code academics} and {@code student} only through their named interfaces, the same
 * discipline every other service in this module holds.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceLeaveService {

    private final AttendanceLeaveRequestRepository leaveRequests;
    private final AcademicsLookup academics;
    private final StudentLookup students;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AttendanceLeaveService(
            AttendanceLeaveRequestRepository leaveRequests,
            AcademicsLookup academics,
            StudentLookup students,
            AuditService audit,
            CurrentUser currentUser) {
        this.leaveRequests = leaveRequests;
        this.academics = academics;
        this.students = students;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /**
     * Files a leave request for a student on {@code sectionId}'s live roster.
     *
     * <p>Refuses a range ending before it starts ({@link AttendanceErrorCode#LEAVE_INVALID_DATE_RANGE})
     * and a range starting before today ({@link AttendanceErrorCode#LEAVE_DATE_IN_PAST}) — see that
     * code's own Javadoc for why a backdated request is refused wholesale rather than routed anywhere
     * automatically: the caller is told to use the correction workflow once a day has actually been
     * marked, not offered a second approval mechanism here.
     */
    @Transactional
    public LeaveRequestResponse create(UUID sectionId, CreateLeaveRequest request) {
        SectionRef section = requireSection(sectionId);
        AcademicSessionRef session = requireCurrentSession();

        if (request.endDate().isBefore(request.startDate())) {
            throw new ChalkbaseException(AttendanceErrorCode.LEAVE_INVALID_DATE_RANGE);
        }
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new ChalkbaseException(AttendanceErrorCode.LEAVE_DATE_IN_PAST);
        }

        Set<UUID> roster = students.rosterOfSection(sectionId, session.id()).stream()
                .map(EnrolledStudentRef::studentId)
                .collect(Collectors.toUnmodifiableSet());
        if (!roster.contains(request.studentId())) {
            throw new ChalkbaseException(
                    AttendanceErrorCode.STUDENT_NOT_ENROLLED_IN_SECTION,
                    AttendanceErrorCode.STUDENT_NOT_ENROLLED_IN_SECTION.defaultMessage(),
                    Map.of("studentId", request.studentId().toString()));
        }

        AttendanceLeaveRequest created = new AttendanceLeaveRequest(
                request.studentId(),
                sectionId,
                session.id(),
                request.startDate(),
                request.endDate(),
                request.reason(),
                currentUser.require());
        leaveRequests.saveAndFlush(created);

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AttendanceAudit.LEAVE_REQUEST,
                created.getId().toString(),
                List.of("startDate", "endDate", "reason"));

        return toResponse(created, section);
    }

    /** One leave request. */
    public LeaveRequestResponse get(UUID id) {
        AttendanceLeaveRequest found = requireRequest(id);
        return toResponse(found, sectionOf(found));
    }

    /** Every leave request, newest first, or only those in {@code decision} if given, oldest first. */
    public PageResponse<LeaveRequestResponse> list(LeaveDecision decision, Pageable pageable) {
        Page<AttendanceLeaveRequest> page = decision != null
                ? leaveRequests.findByDecisionOrderByRequestedAtAsc(decision, pageable)
                : leaveRequests.findAll(pageable);
        return PageResponse.of(page, toResponses(page.getContent()));
    }

    /**
     * Approves or rejects a request. Deliberately does not touch {@code attendance_mark} — see the
     * ADR-0030 amendment. One audit entry results, not two: unlike a correction's approval, there is
     * no second entity this write changes.
     */
    @Transactional
    public LeaveRequestResponse decide(UUID id, DecideLeaveRequest request) {
        if (request.decision() == LeaveDecision.PENDING) {
            throw new ChalkbaseException(AttendanceErrorCode.LEAVE_REQUEST_NOT_PENDING);
        }
        AttendanceLeaveRequest found = requireRequest(id);
        found.decide(request.decision(), currentUser.require(), request.note());
        leaveRequests.saveAndFlush(found);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                AttendanceAudit.LEAVE_REQUEST,
                found.getId().toString(),
                List.of("decision", "decisionNote"));

        return toResponse(found, sectionOf(found));
    }

    private List<LeaveRequestResponse> toResponses(Collection<AttendanceLeaveRequest> requests) {
        Set<UUID> studentIds =
                requests.stream().map(AttendanceLeaveRequest::getStudentId).collect(Collectors.toSet());
        Set<UUID> sectionIds =
                requests.stream().map(AttendanceLeaveRequest::getSectionId).collect(Collectors.toSet());
        Map<UUID, StudentNameRef> names = students.namesOf(studentIds);
        Map<UUID, SectionRef> sections = academics.sections(sectionIds);

        return requests.stream()
                .map(request -> LeaveRequestResponse.of(
                        request,
                        nameOf(names, request.getStudentId()),
                        sectionNameOf(sections, request.getSectionId()),
                        classNameOf(sections, request.getSectionId())))
                .toList();
    }

    private LeaveRequestResponse toResponse(AttendanceLeaveRequest request, SectionRef section) {
        Map<UUID, StudentNameRef> names = students.namesOf(Set.of(request.getStudentId()));
        String sectionName = section != null ? section.name() : "Unknown section";
        String className = section != null ? section.className() : "Unknown class";
        return LeaveRequestResponse.of(request, nameOf(names, request.getStudentId()), sectionName, className);
    }

    private SectionRef sectionOf(AttendanceLeaveRequest request) {
        return academics.sections(Set.of(request.getSectionId())).get(request.getSectionId());
    }

    private static String nameOf(Map<UUID, StudentNameRef> names, UUID studentId) {
        StudentNameRef ref = names.get(studentId);
        return ref != null ? ref.fullName() : "Unknown student";
    }

    private static String sectionNameOf(Map<UUID, SectionRef> sections, UUID sectionId) {
        SectionRef ref = sections.get(sectionId);
        return ref != null ? ref.name() : "Unknown section";
    }

    private static String classNameOf(Map<UUID, SectionRef> sections, UUID sectionId) {
        SectionRef ref = sections.get(sectionId);
        return ref != null ? ref.className() : "Unknown class";
    }

    private SectionRef requireSection(UUID sectionId) {
        return academics.section(sectionId).orElseThrow(() -> new NotFoundException("Section", sectionId));
    }

    private AcademicSessionRef requireCurrentSession() {
        return academics
                .currentSession()
                .orElseThrow(() -> new ChalkbaseException(AttendanceErrorCode.NO_CURRENT_SESSION));
    }

    private AttendanceLeaveRequest requireRequest(UUID id) {
        return leaveRequests.findById(id).orElseThrow(() -> new NotFoundException("Leave request", id));
    }
}
