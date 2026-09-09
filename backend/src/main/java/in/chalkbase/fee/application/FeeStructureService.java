package in.chalkbase.fee.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SchoolClassRef;
import in.chalkbase.fee.api.CopyFeeStructureRequest;
import in.chalkbase.fee.api.CopyFeeStructureResponse;
import in.chalkbase.fee.api.FeeStructureItemRequest;
import in.chalkbase.fee.api.FeeStructureResponse;
import in.chalkbase.fee.api.SaveFeeStructureRequest;
import in.chalkbase.fee.domain.FeeAudit;
import in.chalkbase.fee.domain.FeeErrorCode;
import in.chalkbase.fee.domain.FeeHead;
import in.chalkbase.fee.domain.FeeHeadCategory;
import in.chalkbase.fee.domain.FeeInstallment;
import in.chalkbase.fee.domain.FeeStructure;
import in.chalkbase.fee.domain.FeeStructureItem;
import in.chalkbase.fee.infrastructure.FeeHeadRepository;
import in.chalkbase.fee.infrastructure.FeeStructureRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.platform.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A class's fee structure, versioned per academic session (ADR-0012 rule 6, ADR-0033).
 *
 * <p>Reaches {@code academics} only through {@link AcademicsLookup}, never a join or a domain
 * import.
 *
 * <h2>How an in-place edit of a filed structure is prevented</h2>
 *
 * <p>{@link #save} never updates a row. It always writes the next {@link FeeStructure#getVersion()}
 * for the same {@code (session, class)} and, if a version already existed, marks it
 * {@link FeeStructure#supersede() superseded} in the same transaction — the whole of what
 * "editing" means here.
 *
 * <p>That alone would still let a school rewrite a session's fee history indefinitely, which is
 * exactly the failure ADR-0012 rule 6 exists to close. So a <em>second</em> version is refused
 * once the session has already run its course: {@link #isEditable} treats a session as open for
 * editing while it is either the school's current session, or has not started yet
 * ({@code startsOn} in the future — a school preparing next year's structure ahead of the
 * rollover). A session that has started and is not current can only be inferred to be a past one,
 * since "current" only ever moves forward in this build; such a session refuses a second version
 * with {@link FeeErrorCode#STRUCTURE_SESSION_CLOSED}. The very first version a class ever gets in
 * a session is never refused this way, on any session, so a school onboarding after a session has
 * already ended can still record what it charged for the audit trail — {@code isEditable} is
 * never even consulted for that case.
 *
 * <p>{@link AcademicSessionRef} carries no {@code endsOn} — it is not part of the cross-module
 * contract {@code academics.api} exposes today — so this rule is expressed only in terms of
 * {@code startsOn} and {@code current}, both of which it does carry.
 */
@Service
@Transactional(readOnly = true)
public class FeeStructureService {

    private final FeeStructureRepository structures;
    private final FeeHeadRepository heads;
    private final AcademicsLookup academics;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public FeeStructureService(
            FeeStructureRepository structures,
            FeeHeadRepository heads,
            AcademicsLookup academics,
            AuditService audit,
            CurrentUser currentUser) {
        this.structures = structures;
        this.heads = heads;
        this.academics = academics;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** Every class's live structure for one session, ordered the way the school reads its ladder. */
    public List<FeeStructureResponse> currentStructures(UUID sessionId) {
        AcademicSessionRef session = requireSession(sessionId);
        Map<UUID, SchoolClassRef> classesById = classesById();

        return structures.findByAcademicSessionIdAndSupersededAtIsNull(sessionId).stream()
                .sorted(Comparator.comparing(structure -> sequenceOf(classesById, structure.getSchoolClassId())))
                .map(structure -> FeeStructureResponse.of(
                        structure, session.name(), nameOf(classesById, structure.getSchoolClassId())))
                .toList();
    }

    /** One class's live structure for one session. */
    public FeeStructureResponse currentForClass(UUID sessionId, UUID classId) {
        AcademicSessionRef session = requireSession(sessionId);
        SchoolClassRef schoolClass = requireClass(classId);
        FeeStructure current = requireCurrent(sessionId, classId);
        return FeeStructureResponse.of(current, session.name(), schoolClass.name());
    }

    /**
     * Writes the next version of one class's structure for one session — the whole of what
     * "editing" means here. See the class Javadoc for the lock rule this enforces.
     */
    @Transactional
    public FeeStructureResponse save(UUID sessionId, UUID classId, SaveFeeStructureRequest request) {
        AcademicSessionRef session = requireSession(sessionId);
        SchoolClassRef schoolClass = requireClass(classId);

        FeeStructure existing = structures
                .findByAcademicSessionIdAndSchoolClassIdAndSupersededAtIsNull(sessionId, classId)
                .orElse(null);
        if (existing != null && !isEditable(session)) {
            throw new ChalkbaseException(FeeErrorCode.STRUCTURE_SESSION_CLOSED);
        }
        int version = existing == null ? 1 : existing.getVersion() + 1;

        List<ItemSpec> itemSpecs = request.items().stream().map(ItemSpec::from).toList();
        FeeStructure created = buildVersion(sessionId, classId, session, itemSpecs, version, currentUser.require());

        if (existing != null) {
            existing.supersede();
            structures.save(existing);
        }
        structures.saveAndFlush(created);

        audit.recordChange(
                existing == null ? AuditAction.ENTITY_CREATED : FeeAudit.STRUCTURE_VERSION_SAVED,
                FeeAudit.FEE_STRUCTURE,
                sessionId + "@" + classId,
                List.of("items"));

        return FeeStructureResponse.of(created, session.name(), schoolClass.name());
    }

    /**
     * Copies every class's live structure from {@code fromSessionId} into {@code toSessionId},
     * skipping any class that already has one there. Due dates shift by the gap between the two
     * sessions' start dates, so a term due "10 days into the year" lands 10 days into the new one
     * rather than carrying last year's calendar date forward literally.
     */
    @Transactional
    public CopyFeeStructureResponse copyFromPreviousSession(CopyFeeStructureRequest request) {
        if (request.fromSessionId().equals(request.toSessionId())) {
            throw new ChalkbaseException(FeeErrorCode.CANNOT_COPY_SESSION_INTO_ITSELF);
        }
        AcademicSessionRef fromSession = requireSession(request.fromSessionId());
        AcademicSessionRef toSession = requireSession(request.toSessionId());
        long dayShift = ChronoUnit.DAYS.between(fromSession.startsOn(), toSession.startsOn());
        Map<UUID, SchoolClassRef> classesById = classesById();
        UUID actor = currentUser.require();

        List<String> skipped = new ArrayList<>();
        List<FeeStructureResponse> copied = new ArrayList<>();

        for (FeeStructure source : structures.findByAcademicSessionIdAndSupersededAtIsNull(request.fromSessionId())) {
            UUID classId = source.getSchoolClassId();
            String className = nameOf(classesById, classId);

            boolean alreadyHasOne = structures
                    .findByAcademicSessionIdAndSchoolClassIdAndSupersededAtIsNull(request.toSessionId(), classId)
                    .isPresent();
            if (alreadyHasOne) {
                skipped.add(className);
                continue;
            }

            List<ItemSpec> itemSpecs = source.getItems().stream()
                    .map(item -> ItemSpec.shifted(item, dayShift))
                    .toList();
            FeeStructure created = buildVersion(request.toSessionId(), classId, toSession, itemSpecs, 1, actor);
            structures.saveAndFlush(created);

            audit.recordChange(
                    FeeAudit.STRUCTURE_COPIED,
                    FeeAudit.FEE_STRUCTURE,
                    request.toSessionId() + "@" + classId,
                    List.of("items"));

            copied.add(FeeStructureResponse.of(created, toSession.name(), className));
        }

        return new CopyFeeStructureResponse(skipped, copied);
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    /**
     * A session is open for a new version while it is the school's current one, or has not started
     * yet. Once it has started and stopped being current it can only be a past session, because
     * "current" moves forward only — see the class Javadoc for why this is expressed in terms of
     * {@code startsOn} rather than an end date {@code academics.api} does not expose.
     */
    private static boolean isEditable(AcademicSessionRef session) {
        return session.current() || session.startsOn().isAfter(LocalDate.now());
    }

    /** Validates a full set of items against the fee head catalogue and the Development Fee cap, then persists them. */
    private FeeStructure buildVersion(
            UUID sessionId,
            UUID classId,
            AcademicSessionRef session,
            List<ItemSpec> itemSpecs,
            int version,
            UUID actor) {
        rejectDuplicateHeads(itemSpecs);

        Map<UUID, FeeHead> headsById = itemSpecs.stream()
                .map(ItemSpec::feeHeadId)
                .distinct()
                .collect(Collectors.toMap(id -> id, this::requireActiveFeeHead));

        BigDecimal tuitionTotal = itemSpecs.stream()
                .filter(spec -> headsById.get(spec.feeHeadId()).getCategory() == FeeHeadCategory.TUITION)
                .map(ItemSpec::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FeeStructure structure = new FeeStructure(sessionId, classId, version, actor);
        for (ItemSpec spec : itemSpecs) {
            FeeHead head = headsById.get(spec.feeHeadId());
            rejectCapViolation(head, spec.amount(), tuitionTotal);
            rejectBadInstallments(spec, session);

            FeeStructureItem item = new FeeStructureItem(structure, head, spec.amount(), spec.frequency());
            for (InstallmentSpec installment : spec.installments()) {
                item.addInstallment(new FeeInstallment(item, installment.dueDate(), installment.amount()));
            }
            structure.addItem(item);
        }
        return structure;
    }

    private static void rejectDuplicateHeads(List<ItemSpec> itemSpecs) {
        Set<UUID> seen = new HashSet<>();
        for (ItemSpec spec : itemSpecs) {
            if (!seen.add(spec.feeHeadId())) {
                throw new ChalkbaseException(FeeErrorCode.DUPLICATE_HEAD_IN_STRUCTURE);
            }
        }
    }

    private FeeHead requireActiveFeeHead(UUID id) {
        FeeHead head = heads.findById(id).orElseThrow(() -> new NotFoundException("Fee head", id));
        if (!head.isActive()) {
            throw new ChalkbaseException(FeeErrorCode.INACTIVE_FEE_HEAD);
        }
        return head;
    }

    private static void rejectCapViolation(FeeHead head, BigDecimal amount, BigDecimal tuitionTotal) {
        BigDecimal capPercent = head.getCapPercentOfTuition();
        if (capPercent == null) {
            return;
        }
        BigDecimal cap = tuitionTotal.multiply(capPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (amount.compareTo(cap) > 0) {
            throw new ChalkbaseException(FeeErrorCode.DEVELOPMENT_FEE_EXCEEDS_CAP);
        }
    }

    private static void rejectBadInstallments(ItemSpec spec, AcademicSessionRef session) {
        Set<LocalDate> dueDates = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (InstallmentSpec installment : spec.installments()) {
            if (!dueDates.add(installment.dueDate())) {
                throw new ChalkbaseException(FeeErrorCode.DUPLICATE_INSTALLMENT_DATE);
            }
            if (installment.dueDate().isBefore(session.startsOn())) {
                throw new ChalkbaseException(FeeErrorCode.INSTALLMENT_BEFORE_SESSION_START);
            }
            sum = sum.add(installment.amount());
        }
        if (sum.compareTo(spec.amount()) != 0) {
            throw new ChalkbaseException(FeeErrorCode.INSTALLMENTS_DO_NOT_SUM_TO_AMOUNT);
        }
    }

    private AcademicSessionRef requireSession(UUID sessionId) {
        return academics.session(sessionId).orElseThrow(() -> new NotFoundException("Academic session", sessionId));
    }

    private SchoolClassRef requireClass(UUID classId) {
        SchoolClassRef schoolClass = classesById().get(classId);
        if (schoolClass == null) {
            throw new NotFoundException("Class", classId);
        }
        return schoolClass;
    }

    private FeeStructure requireCurrent(UUID sessionId, UUID classId) {
        return structures
                .findByAcademicSessionIdAndSchoolClassIdAndSupersededAtIsNull(sessionId, classId)
                .orElseThrow(() -> new NotFoundException("Fee structure", classId + "@" + sessionId));
    }

    private Map<UUID, SchoolClassRef> classesById() {
        return academics.classes().stream().collect(Collectors.toMap(SchoolClassRef::id, ref -> ref));
    }

    private static String nameOf(Map<UUID, SchoolClassRef> classesById, UUID classId) {
        SchoolClassRef ref = classesById.get(classId);
        return ref == null ? "Unknown class" : ref.name();
    }

    private static int sequenceOf(Map<UUID, SchoolClassRef> classesById, UUID classId) {
        SchoolClassRef ref = classesById.get(classId);
        return ref == null ? Integer.MAX_VALUE : ref.sequence();
    }

    /** One fee head's amount, frequency and installments, whatever request or previous structure it came from. */
    private record ItemSpec(
            UUID feeHeadId, BigDecimal amount, InstallmentFrequency frequency, List<InstallmentSpec> installments) {

        static ItemSpec from(FeeStructureItemRequest request) {
            return new ItemSpec(
                    request.feeHeadId(),
                    request.amount(),
                    request.frequency(),
                    request.installments().stream()
                            .map(installment -> new InstallmentSpec(installment.dueDate(), installment.amount()))
                            .toList());
        }

        static ItemSpec shifted(FeeStructureItem item, long dayShift) {
            return new ItemSpec(
                    item.getFeeHead().getId(),
                    item.getAmount(),
                    item.getFrequency(),
                    item.getInstallments().stream()
                            .map(installment -> new InstallmentSpec(
                                    installment.getDueDate().plusDays(dayShift), installment.getAmount()))
                            .toList());
        }
    }

    private record InstallmentSpec(LocalDate dueDate, BigDecimal amount) {}
}
