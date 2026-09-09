package in.chalkbase.admission.application;

import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SchoolClassRef;
import in.chalkbase.admission.api.AssignCounsellorRequest;
import in.chalkbase.admission.api.CreateEnquiryRequest;
import in.chalkbase.admission.api.EnquiryDetailResponse;
import in.chalkbase.admission.api.EnquiryFollowUpQueueItem;
import in.chalkbase.admission.api.EnquiryFollowUpResponse;
import in.chalkbase.admission.api.EnquirySummary;
import in.chalkbase.admission.api.LogFollowUpRequest;
import in.chalkbase.admission.domain.AdmissionAudit;
import in.chalkbase.admission.domain.AdmissionErrorCode;
import in.chalkbase.admission.domain.Enquiry;
import in.chalkbase.admission.domain.EnquiryFollowUp;
import in.chalkbase.admission.domain.EnquiryQuery;
import in.chalkbase.admission.domain.EnquiryStatus;
import in.chalkbase.admission.infrastructure.EnquiryFollowUpRepository;
import in.chalkbase.admission.infrastructure.EnquiryQueries;
import in.chalkbase.admission.infrastructure.EnquiryRepository;
import in.chalkbase.identity.api.IdentityLookup;
import in.chalkbase.identity.api.UserSummary;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.CurrentUser;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
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
 * Capturing, viewing, assigning and following up this school's admission enquiries (FR-016,
 * FR-017).
 *
 * <p>Reaches {@code academics} and {@code identity} only through their named interfaces —
 * {@link AcademicsLookup} and {@link IdentityLookup} — never through a join or a domain import.
 *
 * <p><strong>Audited per ADR-0018 and AGENTS rule 11</strong>: every write records the NAMES of the
 * fields it changed, in the same transaction as the change. The {@code entityId} is always the
 * enquiry's UUID, never the child's name or the parent's phone number, both Confidential under
 * ADR-0014.
 */
@Service
@Transactional(readOnly = true)
public class AdmissionEnquiryService {

    private static final Set<EnquiryStatus> CLOSED_STATUSES = Set.of(EnquiryStatus.CONVERTED, EnquiryStatus.LOST);
    private static final String UNKNOWN_ACCOUNT_NAME = "Unknown account";

    private final EnquiryRepository enquiries;
    private final EnquiryFollowUpRepository followUps;
    private final AcademicsLookup academics;
    private final IdentityLookup identity;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AdmissionEnquiryService(
            EnquiryRepository enquiries,
            EnquiryFollowUpRepository followUps,
            AcademicsLookup academics,
            IdentityLookup identity,
            AuditService audit,
            CurrentUser currentUser) {
        this.enquiries = enquiries;
        this.followUps = followUps;
        this.academics = academics;
        this.identity = identity;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** One page of the enquiry list, filtered — the front office's own view. */
    public PageResponse<EnquirySummary> list(EnquiryQuery query, Pageable pageable) {
        Page<Enquiry> page = enquiries.findAll(EnquiryQueries.matching(query), pageable);
        List<Enquiry> content = page.getContent();

        Map<UUID, String> classNames = classNameIndex();
        Map<UUID, UserSummary> counsellors = identity.usersOf(counsellorIdsOf(content));

        List<EnquirySummary> rows = content.stream()
                .map(enquiry -> EnquirySummary.of(
                        enquiry,
                        classNames.get(enquiry.getInterestedClassId()),
                        nameOf(counsellors, enquiry.getAssignedCounsellorId())))
                .toList();
        return PageResponse.of(page, rows);
    }

    /** One enquiry, in full, with its whole follow-up history. */
    public EnquiryDetailResponse detail(UUID id) {
        return toDetail(requireEnquiry(id));
    }

    /** Captures a new enquiry (FR-016). */
    @Transactional
    public EnquiryDetailResponse create(CreateEnquiryRequest request) {
        if (request.interestedClassId() != null) {
            requireClass(request.interestedClassId());
        }
        requireActiveCounsellor(request.assignedCounsellorId());

        Enquiry enquiry = new Enquiry(
                request.childFullName().trim(),
                request.childDateOfBirth(),
                request.interestedClassId(),
                request.parentName().trim(),
                request.parentPhone().trim(),
                blankToNull(request.parentEmail()),
                request.source(),
                blankToNull(request.remarks()),
                request.assignedCounsellorId(),
                currentUser.require());
        enquiries.saveAndFlush(enquiry);

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AdmissionAudit.ENQUIRY,
                enquiry.getId().toString(),
                List.of("childFullName", "parentName", "parentPhone", "source", "assignedCounsellorId"));

        return toDetail(enquiry);
    }

    /** Assigns or reassigns the counsellor responsible for one enquiry's follow-up. */
    @Transactional
    public EnquiryDetailResponse assign(UUID id, AssignCounsellorRequest request) {
        Enquiry enquiry = requireEnquiry(id);
        requireActiveCounsellor(request.counsellorId());

        enquiry.reassign(request.counsellorId());
        enquiries.saveAndFlush(enquiry);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED, AdmissionAudit.ENQUIRY, id.toString(), List.of("assignedCounsellorId"));

        return toDetail(enquiry);
    }

    /**
     * Logs a follow-up against one enquiry, and applies its effect on the enquiry's own status and
     * next follow-up date (see {@link Enquiry#applyFollowUp}).
     *
     * @throws ChalkbaseException {@link AdmissionErrorCode#ENQUIRY_CLOSED},
     *     {@link AdmissionErrorCode#STATUS_CANNOT_REOPEN_TO_NEW} or
     *     {@link AdmissionErrorCode#NEXT_FOLLOW_UP_DATE_REQUIRED} — all raised by the entity, not
     *     this method, because they are rules about the enquiry's own state
     */
    @Transactional
    public EnquiryDetailResponse logFollowUp(UUID id, LogFollowUpRequest request) {
        Enquiry enquiry = requireEnquiry(id);
        UUID actor = currentUser.require();
        EnquiryStatus statusBefore = enquiry.getStatus();

        enquiry.applyFollowUp(request.resultingStatus(), request.nextFollowUpDate());
        enquiries.saveAndFlush(enquiry);

        // What actually changed, not merely what the caller asked for: a fresh enquiry moves itself
        // from NEW to IN_PROGRESS on any follow-up at all (Enquiry#applyFollowUp), even when nobody
        // named a resultingStatus — and the history should say so happened, not read as though
        // nothing changed that day.
        EnquiryStatus recordedResultingStatus = enquiry.getStatus() == statusBefore ? null : enquiry.getStatus();

        EnquiryFollowUp entry = new EnquiryFollowUp(
                id, request.note().trim(), enquiry.getNextFollowUpDate(), recordedResultingStatus, actor);
        followUps.saveAndFlush(entry);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                AdmissionAudit.ENQUIRY,
                id.toString(),
                List.of("status", "nextFollowUpDate"));
        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AdmissionAudit.FOLLOW_UP,
                entry.getId().toString(),
                List.of("note", "nextFollowUpDate", "resultingStatus"));

        return toDetail(enquiry);
    }

    /**
     * The due-date follow-up queue — the thing that makes this more than a mailbox. See
     * {@code EnquiryRepository.findDueFollowUps}.
     *
     * @param mine true for the caller's own assigned enquiries (a counsellor's Monday morning),
     *     false for every counsellor's. There is no scope enforcing "mine" as a boundary — see
     *     {@code AdmissionPermissions}'s own note on why — so {@code false} is available to anyone
     *     holding {@code admission:enquiry:read}, the same honesty {@code AttendancePermissions}
     *     states for section-scoped attendance.
     */
    public PageResponse<EnquiryFollowUpQueueItem> dueFollowUps(boolean mine, Pageable pageable) {
        LocalDate today = LocalDate.now();
        Page<Enquiry> page = mine
                ? enquiries.findDueFollowUpsForCounsellor(today, CLOSED_STATUSES, currentUser.require(), pageable)
                : enquiries.findDueFollowUps(today, CLOSED_STATUSES, pageable);
        List<Enquiry> content = page.getContent();

        Map<UUID, String> classNames = classNameIndex();
        Map<UUID, UserSummary> counsellors = identity.usersOf(counsellorIdsOf(content));

        List<EnquiryFollowUpQueueItem> rows = content.stream()
                .map(enquiry -> EnquiryFollowUpQueueItem.of(
                        enquiry,
                        classNames.get(enquiry.getInterestedClassId()),
                        nameOf(counsellors, enquiry.getAssignedCounsellorId()),
                        today))
                .toList();
        return PageResponse.of(page, rows);
    }

    /** Every account this school could assign an enquiry to — the assignment picker's own read. */
    public List<UserSummary> counsellors() {
        return identity.activeUsers();
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    private EnquiryDetailResponse toDetail(Enquiry enquiry) {
        List<EnquiryFollowUp> history = followUps.findByEnquiryIdOrderByRecordedAtDesc(enquiry.getId());

        Set<UUID> userIds = new LinkedHashSet<>();
        userIds.add(enquiry.getAssignedCounsellorId());
        userIds.add(enquiry.getCapturedBy());
        history.forEach(entry -> userIds.add(entry.getRecordedBy()));
        Map<UUID, UserSummary> users = identity.usersOf(userIds);

        List<EnquiryFollowUpResponse> followUpRows = history.stream()
                .map(entry -> EnquiryFollowUpResponse.of(entry, nameOf(users, entry.getRecordedBy())))
                .toList();

        return EnquiryDetailResponse.of(
                enquiry,
                classNameOf(enquiry.getInterestedClassId()),
                nameOf(users, enquiry.getAssignedCounsellorId()),
                nameOf(users, enquiry.getCapturedBy()),
                followUpRows);
    }

    private String classNameOf(UUID classId) {
        if (classId == null) {
            return null;
        }
        return classNameIndex().get(classId);
    }

    /** The whole ladder, resolved once per call site — fourteen rows, not worth caching further. */
    private Map<UUID, String> classNameIndex() {
        return academics.classes().stream().collect(Collectors.toMap(SchoolClassRef::id, SchoolClassRef::name));
    }

    private static Set<UUID> counsellorIdsOf(Collection<Enquiry> rows) {
        return rows.stream().map(Enquiry::getAssignedCounsellorId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String nameOf(Map<UUID, UserSummary> users, UUID id) {
        UserSummary user = users.get(id);
        return user != null ? user.displayName() : UNKNOWN_ACCOUNT_NAME;
    }

    private void requireClass(UUID classId) {
        boolean exists = academics.classes().stream().map(SchoolClassRef::id).anyMatch(classId::equals);
        if (!exists) {
            throw new NotFoundException("Class", classId);
        }
    }

    private void requireActiveCounsellor(UUID counsellorId) {
        UserSummary user = identity.usersOf(Set.of(counsellorId)).get(counsellorId);
        if (user == null) {
            throw new NotFoundException("Counsellor", counsellorId);
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new ChalkbaseException(AdmissionErrorCode.COUNSELLOR_NOT_ACTIVE);
        }
    }

    private Enquiry requireEnquiry(UUID id) {
        return enquiries.findById(id).orElseThrow(() -> new NotFoundException("Enquiry", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
