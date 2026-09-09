package in.chalkbase.fee.application;

import in.chalkbase.fee.api.FeeHeadResponse;
import in.chalkbase.fee.api.SaveFeeHeadRequest;
import in.chalkbase.fee.domain.FeeAudit;
import in.chalkbase.fee.domain.FeeErrorCode;
import in.chalkbase.fee.domain.FeeHead;
import in.chalkbase.fee.domain.FeeHeadCategory;
import in.chalkbase.fee.infrastructure.FeeHeadRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The school's catalogue of fee heads (Phase 0 §4, ADR-0012).
 *
 * <p>Scoped to the school bound to this request and no other — the schema is the tenant boundary
 * (ADR-0011).
 *
 * <p><strong>Nothing here deletes.</strong> A head already named by a fee structure item must not
 * point at nothing; a head created by mistake is retired.
 */
@Service
@Transactional(readOnly = true)
public class FeeHeadService {

    private final FeeHeadRepository heads;
    private final AuditService audit;

    public FeeHeadService(FeeHeadRepository heads, AuditService audit) {
        this.heads = heads;
        this.audit = audit;
    }

    public List<FeeHeadResponse> list() {
        return heads.findAllByOrderByNameAsc().stream().map(FeeHeadResponse::of).toList();
    }

    @Transactional
    public FeeHeadResponse create(SaveFeeHeadRequest request) {
        rejectCapOnNonDevelopmentHead(request.category(), request.capPercentOfTuition());

        FeeHead head = new FeeHead(request.name().trim(), request.category(), request.capPercentOfTuition());
        head.setActive(request.active());
        FeeHead created = heads.saveAndFlush(head);

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                FeeAudit.FEE_HEAD,
                created.getId().toString(),
                List.of("name", "category", "capPercentOfTuition"));

        return FeeHeadResponse.of(created);
    }

    @Transactional
    public FeeHeadResponse update(UUID id, SaveFeeHeadRequest request) {
        rejectCapOnNonDevelopmentHead(request.category(), request.capPercentOfTuition());

        FeeHead head = heads.findById(id).orElseThrow(() -> new NotFoundException("Fee head", id));
        String name = request.name().trim();

        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(head.getName(), name)) {
            changed.add("name");
        }
        if (head.getCategory() != request.category()) {
            changed.add("category");
        }
        if (!amountsEqual(head.getCapPercentOfTuition(), request.capPercentOfTuition())) {
            changed.add("capPercentOfTuition");
        }
        if (head.isActive() != request.active()) {
            changed.add("active");
        }
        if (changed.isEmpty()) {
            return FeeHeadResponse.of(head);
        }

        head.apply(name, request.category(), request.capPercentOfTuition());
        head.setActive(request.active());
        heads.saveAndFlush(head);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED, FeeAudit.FEE_HEAD, head.getId().toString(), changed);

        return FeeHeadResponse.of(head);
    }

    private static void rejectCapOnNonDevelopmentHead(FeeHeadCategory category, BigDecimal capPercentOfTuition) {
        if (capPercentOfTuition != null && category != FeeHeadCategory.ANNUAL_DEVELOPMENT) {
            throw new ChalkbaseException(FeeErrorCode.CAP_PERCENT_NOT_APPLICABLE);
        }
    }

    /**
     * {@code BigDecimal.equals} is scale-sensitive ({@code 15.0} and {@code 15.00} are "different"),
     * which would record a spurious {@code capPercentOfTuition} change on every save that merely
     * round-trips the same percentage through a different scale. Value equality is what the audit
     * log's "did this actually change" question means here.
     */
    private static boolean amountsEqual(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }
}
