package in.chalkbase.communication.application;

import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SchoolClassRef;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.communication.api.AcknowledgeRecipientRequest;
import in.chalkbase.communication.api.CircularRecipientResponse;
import in.chalkbase.communication.domain.Circular;
import in.chalkbase.communication.domain.CircularRecipient;
import in.chalkbase.communication.domain.CommunicationAudit;
import in.chalkbase.communication.domain.CommunicationErrorCode;
import in.chalkbase.communication.infrastructure.CircularRecipientRepository;
import in.chalkbase.communication.infrastructure.CircularRepository;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.CurrentUser;
import in.chalkbase.student.api.StudentLookup;
import in.chalkbase.student.api.StudentNameRef;
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
 * A published circular's own recipient list, and recording an acknowledgement on one.
 *
 * <p>Separate from {@link CircularService}: composing and publishing is
 * {@code communication:circular:manage}, and recording an acknowledgement is
 * {@code communication:circular:acknowledge} — two different acts, the same split
 * {@code AttendanceCorrectionService} draws from {@code AttendanceMarkingService}.
 */
@Service
@Transactional(readOnly = true)
public class CircularRecipientService {

    private final CircularRecipientRepository recipients;
    private final CircularRepository circulars;
    private final AcademicsLookup academics;
    private final StudentLookup students;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public CircularRecipientService(
            CircularRecipientRepository recipients,
            CircularRepository circulars,
            AcademicsLookup academics,
            StudentLookup students,
            AuditService audit,
            CurrentUser currentUser) {
        this.recipients = recipients;
        this.circulars = circulars;
        this.academics = academics;
        this.students = students;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** A circular's recipients, in the order they were generated at publish time. */
    public PageResponse<CircularRecipientResponse> list(UUID circularId, Pageable pageable) {
        requireCircular(circularId);
        Page<CircularRecipient> page = recipients.findByCircularIdOrderByCreatedAtAsc(circularId, pageable);
        return PageResponse.of(page, toResponses(page.getContent()));
    }

    /**
     * Records that a recipient's family has acknowledged the circular, on their behalf.
     *
     * <p>Refused for a circular that was not composed to require one
     * ({@link CommunicationErrorCode#ACKNOWLEDGEMENT_NOT_REQUIRED}) and for a recipient who
     * already has one ({@link CommunicationErrorCode#ALREADY_ACKNOWLEDGED}, via
     * {@link CircularRecipient#acknowledge}).
     */
    @Transactional
    public CircularRecipientResponse acknowledge(
            UUID circularId, UUID recipientId, AcknowledgeRecipientRequest request) {
        Circular circular = requireCircular(circularId);
        if (!circular.isRequiresAcknowledgement()) {
            throw new ChalkbaseException(CommunicationErrorCode.ACKNOWLEDGEMENT_NOT_REQUIRED);
        }
        CircularRecipient recipient = recipients
                .findById(recipientId)
                .filter(row -> row.getCircularId().equals(circularId))
                .orElseThrow(() -> new NotFoundException("Circular recipient", recipientId));

        recipient.acknowledge(currentUser.require(), request.note());
        recipients.saveAndFlush(recipient);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                CommunicationAudit.CIRCULAR_RECIPIENT,
                recipient.getId().toString(),
                List.of("acknowledgedAt", "acknowledgementNote"));

        return toResponse(recipient);
    }

    // ── Assembly ─────────────────────────────────────────────────────────────────────────────

    private List<CircularRecipientResponse> toResponses(List<CircularRecipient> rows) {
        Set<UUID> studentIds =
                rows.stream().map(CircularRecipient::getStudentId).collect(Collectors.toSet());
        Set<UUID> sectionIds =
                rows.stream().map(CircularRecipient::getSectionId).collect(Collectors.toSet());
        Map<UUID, StudentNameRef> names = students.namesOf(studentIds);
        Map<UUID, SectionRef> sections = academics.sections(sectionIds);
        Map<UUID, SchoolClassRef> classesById =
                academics.classes().stream().collect(Collectors.toMap(SchoolClassRef::id, c -> c));

        return rows.stream()
                .map(row -> {
                    StudentNameRef name = names.get(row.getStudentId());
                    SectionRef section = sections.get(row.getSectionId());
                    SchoolClassRef schoolClass = section != null ? classesById.get(section.classId()) : null;
                    return CircularRecipientResponse.of(
                            row,
                            name != null ? name.fullName() : "Unknown student",
                            name != null ? name.admissionNumber() : "",
                            section != null ? section.name() : "Unknown section",
                            schoolClass != null ? schoolClass.name() : "Unknown class");
                })
                .toList();
    }

    private CircularRecipientResponse toResponse(CircularRecipient recipient) {
        return toResponses(List.of(recipient)).get(0);
    }

    private Circular requireCircular(UUID circularId) {
        return circulars.findById(circularId).orElseThrow(() -> new NotFoundException("Circular", circularId));
    }
}
