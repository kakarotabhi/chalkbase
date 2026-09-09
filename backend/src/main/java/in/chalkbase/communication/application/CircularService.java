package in.chalkbase.communication.application;

import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SchoolClassRef;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.communication.api.CircularDetail;
import in.chalkbase.communication.api.CircularSummary;
import in.chalkbase.communication.api.CircularTargetRequest;
import in.chalkbase.communication.api.CircularTargetResponse;
import in.chalkbase.communication.api.CreateCircularRequest;
import in.chalkbase.communication.domain.Circular;
import in.chalkbase.communication.domain.CircularRecipient;
import in.chalkbase.communication.domain.CircularTarget;
import in.chalkbase.communication.domain.CommunicationAudit;
import in.chalkbase.communication.domain.CommunicationErrorCode;
import in.chalkbase.communication.infrastructure.CircularRecipientRepository;
import in.chalkbase.communication.infrastructure.CircularRepository;
import in.chalkbase.communication.infrastructure.CircularTargetRepository;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.CurrentUser;
import in.chalkbase.student.api.EnrolledStudentRef;
import in.chalkbase.student.api.StudentLookup;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composing, reading, and publishing circulars.
 *
 * <p>Reaches {@code academics} and {@code student} only through their named interfaces —
 * {@link AcademicsLookup} and {@link StudentLookup} — never through a join or a domain import.
 *
 * <p>{@link #targetPreview} and {@link #resolveRecipients} share one resolution rule, worth
 * stating once: a target names a class (every active section of it) or one section of it, and
 * students are counted by <strong>who they are</strong>, not by how many targets named them — two
 * overlapping targets in the same circular (a whole-class target and one of its own sections) do
 * not double-count a student who appears in both. {@link #resolveRecipients} is where that
 * de-duplication happens, keyed by student id.
 */
@Service
@Transactional(readOnly = true)
public class CircularService {

    private final CircularRepository circulars;
    private final CircularTargetRepository targets;
    private final CircularRecipientRepository recipients;
    private final AcademicsLookup academics;
    private final StudentLookup students;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public CircularService(
            CircularRepository circulars,
            CircularTargetRepository targets,
            CircularRecipientRepository recipients,
            AcademicsLookup academics,
            StudentLookup students,
            AuditService audit,
            CurrentUser currentUser) {
        this.circulars = circulars;
        this.targets = targets;
        this.recipients = recipients;
        this.academics = academics;
        this.students = students;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** Every circular, newest first, draft and published together. */
    public PageResponse<CircularSummary> list(Pageable pageable) {
        Page<Circular> page = circulars.findAllByOrderByCreatedAtDesc(pageable);
        List<CircularSummary> content =
                page.getContent().stream().map(this::toSummary).toList();
        return PageResponse.of(page, content);
    }

    /** One circular in full, including its targets and current recipient counts. */
    public CircularDetail get(UUID circularId) {
        return toDetail(requireCircular(circularId));
    }

    /**
     * Composes a new circular in {@code DRAFT}, with every target given. Each target must name a
     * class this school teaches and, if given, a section that belongs to it
     * ({@link CommunicationErrorCode#INVALID_TARGET}). Two targets naming the same class-with-no-
     * section, or the same section, are refused by the database
     * ({@link CommunicationErrorCode#DUPLICATE_TARGET}).
     */
    @Transactional
    public CircularDetail create(CreateCircularRequest request) {
        Circular circular =
                new Circular(request.title(), request.body(), request.requiresAcknowledgement(), currentUser.require());
        circulars.saveAndFlush(circular);

        for (CircularTargetRequest target : request.targets()) {
            validateTarget(target.classId(), target.sectionId());
            targets.save(new CircularTarget(circular.getId(), target.classId(), target.sectionId()));
        }
        targets.flush();

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                CommunicationAudit.CIRCULAR,
                circular.getId().toString(),
                List.of("title", "body", "requiresAcknowledgement", "targets"));

        return toDetail(circular);
    }

    /**
     * How many actively enrolled students one candidate target would reach, before it is added to
     * a circular being composed. Zero if the class or section does not resolve in this school, or
     * this school has not set a current academic session (the same "not in this school" shape
     * {@link AcademicsLookup} answers everywhere else, rather than an exception for what is, from
     * the composer's point of view, an ordinary answer).
     */
    public TargetPreviewResponse targetPreview(UUID classId, UUID sectionId) {
        return new TargetPreviewResponse(resolveRecipients(List.of(new CircularTargetRequest(classId, sectionId)))
                .size());
    }

    /**
     * Publishes a circular: locks it against further edits and generates one
     * {@link CircularRecipient} per actively enrolled student its targets resolve to.
     *
     * <p>Refuses a circular already published
     * ({@link CommunicationErrorCode#CIRCULAR_ALREADY_PUBLISHED}, via {@link Circular#publish}) and
     * one whose targets resolve to nobody at all
     * ({@link CommunicationErrorCode#CIRCULAR_NO_RECIPIENTS}) — publishing it anyway would create a
     * circular indistinguishable from one that silently failed.
     */
    @Transactional
    public CircularDetail publish(UUID circularId) {
        Circular circular = requireCircular(circularId);
        List<CircularTarget> targetRows = targets.findByCircularIdOrderByCreatedAtAsc(circularId);
        List<CircularTargetRequest> targetRequests = targetRows.stream()
                .map(t -> new CircularTargetRequest(t.getClassId(), t.getSectionId()))
                .toList();

        Map<UUID, TargetedStudent> audience = resolveRecipients(targetRequests);
        if (audience.isEmpty()) {
            throw new ChalkbaseException(CommunicationErrorCode.CIRCULAR_NO_RECIPIENTS);
        }

        circular.publish(currentUser.require());
        circulars.saveAndFlush(circular);

        for (TargetedStudent candidate : audience.values()) {
            recipients.save(
                    new CircularRecipient(circularId, candidate.student().studentId(), candidate.sectionId()));
        }
        recipients.flush();

        audit.recordBulkChange(
                CommunicationAudit.PUBLISHED,
                CommunicationAudit.CIRCULAR,
                circularId.toString(),
                List.of("status", "publishedAt"),
                audience.size());

        return toDetail(circular);
    }

    // ── Resolution ───────────────────────────────────────────────────────────────────────────

    /**
     * One targeted student, and the section they were resolved through — see
     * {@link CircularRecipient#getSectionId()}.
     */
    private record TargetedStudent(EnrolledStudentRef student, UUID sectionId) {}

    /**
     * Every student the given targets resolve to, de-duplicated by student id (see the class
     * Javadoc). A target naming a class with no current session, or a class/section this school
     * does not currently teach, simply contributes nobody rather than throwing — the same "not in
     * this school" shape every {@link AcademicsLookup} read uses.
     */
    private Map<UUID, TargetedStudent> resolveRecipients(List<CircularTargetRequest> requests) {
        Optional<AcademicSessionRef> session = academics.currentSession();
        if (session.isEmpty()) {
            return Map.of();
        }
        UUID sessionId = session.get().id();

        Map<UUID, TargetedStudent> byStudent = new LinkedHashMap<>();
        for (CircularTargetRequest request : requests) {
            List<SectionRef> resolvedSections = request.sectionId() != null
                    ? academics.section(request.sectionId()).map(List::of).orElse(List.of())
                    : academics.sections().stream()
                            .filter(s -> s.active() && s.classId().equals(request.classId()))
                            .toList();
            for (SectionRef section : resolvedSections) {
                for (EnrolledStudentRef student : students.rosterOfSection(section.id(), sessionId)) {
                    byStudent.putIfAbsent(student.studentId(), new TargetedStudent(student, section.id()));
                }
            }
        }
        return byStudent;
    }

    /** {@link CommunicationErrorCode#INVALID_TARGET} for a class this school does not teach, or a section not in it. */
    private void validateTarget(UUID classId, UUID sectionId) {
        boolean classExists = academics.classes().stream().anyMatch(c -> c.id().equals(classId));
        if (!classExists) {
            throw new ChalkbaseException(CommunicationErrorCode.INVALID_TARGET);
        }
        if (sectionId != null) {
            SectionRef section = academics
                    .section(sectionId)
                    .orElseThrow(() -> new ChalkbaseException(CommunicationErrorCode.INVALID_TARGET));
            if (!section.classId().equals(classId)) {
                throw new ChalkbaseException(CommunicationErrorCode.INVALID_TARGET);
            }
        }
    }

    // ── Assembly ─────────────────────────────────────────────────────────────────────────────

    private CircularSummary toSummary(Circular circular) {
        long targetCount = targets.countByCircularId(circular.getId());
        long recipientCount = recipients.countByCircularId(circular.getId());
        long acknowledgedCount = recipients.countByCircularIdAndAcknowledgedAtIsNotNull(circular.getId());
        return new CircularSummary(
                circular.getId(),
                circular.getTitle(),
                circular.getStatus(),
                circular.isRequiresAcknowledgement(),
                (int) targetCount,
                (int) recipientCount,
                (int) acknowledgedCount,
                circular.getPublishedAt(),
                circular.getCreatedAt());
    }

    private CircularDetail toDetail(Circular circular) {
        List<CircularTarget> targetRows = targets.findByCircularIdOrderByCreatedAtAsc(circular.getId());

        Map<UUID, SchoolClassRef> classesById =
                academics.classes().stream().collect(Collectors.toMap(SchoolClassRef::id, c -> c));
        List<UUID> sectionIds = targetRows.stream()
                .map(CircularTarget::getSectionId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, SectionRef> sectionsById = academics.sections(sectionIds);

        List<CircularTargetResponse> targetResponses = targetRows.stream()
                .map(target -> {
                    SchoolClassRef schoolClass = classesById.get(target.getClassId());
                    String className = schoolClass != null ? schoolClass.name() : "Unknown class";
                    SectionRef section = target.getSectionId() != null ? sectionsById.get(target.getSectionId()) : null;
                    String sectionName = section != null ? section.name() : null;
                    return CircularTargetResponse.of(target, className, sectionName);
                })
                .toList();

        long recipientCount = recipients.countByCircularId(circular.getId());
        long acknowledgedCount = recipients.countByCircularIdAndAcknowledgedAtIsNotNull(circular.getId());

        return new CircularDetail(
                circular.getId(),
                circular.getTitle(),
                circular.getBody(),
                circular.isRequiresAcknowledgement(),
                circular.getStatus(),
                targetResponses,
                (int) recipientCount,
                (int) acknowledgedCount,
                circular.getPublishedAt(),
                circular.getCreatedAt());
    }

    private Circular requireCircular(UUID circularId) {
        return circulars.findById(circularId).orElseThrow(() -> new NotFoundException("Circular", circularId));
    }
}
