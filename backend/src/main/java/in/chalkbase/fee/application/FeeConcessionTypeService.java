package in.chalkbase.fee.application;

import in.chalkbase.fee.api.FeeConcessionTypeResponse;
import in.chalkbase.fee.api.SaveFeeConcessionTypeRequest;
import in.chalkbase.fee.domain.FeeAudit;
import in.chalkbase.fee.domain.FeeConcessionType;
import in.chalkbase.fee.infrastructure.FeeConcessionTypeRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.NotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The school's catalogue of concession types (FR-078's "define" half — see
 * {@code in.chalkbase.fee.package-info} for what "apply" would mean and why it is not here).
 *
 * <p>Scoped to the school bound to this request and no other (ADR-0011). Nothing here deletes, the
 * same reasoning {@link FeeHeadService} follows.
 */
@Service
@Transactional(readOnly = true)
public class FeeConcessionTypeService {

    private final FeeConcessionTypeRepository concessionTypes;
    private final AuditService audit;

    public FeeConcessionTypeService(FeeConcessionTypeRepository concessionTypes, AuditService audit) {
        this.concessionTypes = concessionTypes;
        this.audit = audit;
    }

    public List<FeeConcessionTypeResponse> list() {
        return concessionTypes.findAllByOrderByNameAsc().stream()
                .map(FeeConcessionTypeResponse::of)
                .toList();
    }

    @Transactional
    public FeeConcessionTypeResponse create(SaveFeeConcessionTypeRequest request) {
        FeeConcessionType type = new FeeConcessionType(
                request.name().trim(), request.category(), request.description(), request.requiresApproval());
        type.setActive(request.active());
        FeeConcessionType created = concessionTypes.saveAndFlush(type);

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                FeeAudit.FEE_CONCESSION_TYPE,
                created.getId().toString(),
                List.of("name", "category", "description", "requiresApproval"));

        return FeeConcessionTypeResponse.of(created);
    }

    @Transactional
    public FeeConcessionTypeResponse update(UUID id, SaveFeeConcessionTypeRequest request) {
        FeeConcessionType type =
                concessionTypes.findById(id).orElseThrow(() -> new NotFoundException("Concession type", id));
        String name = request.name().trim();

        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(type.getName(), name)) {
            changed.add("name");
        }
        if (type.getCategory() != request.category()) {
            changed.add("category");
        }
        if (!Objects.equals(type.getDescription(), request.description())) {
            changed.add("description");
        }
        if (type.isRequiresApproval() != request.requiresApproval()) {
            changed.add("requiresApproval");
        }
        if (type.isActive() != request.active()) {
            changed.add("active");
        }
        if (changed.isEmpty()) {
            return FeeConcessionTypeResponse.of(type);
        }

        type.apply(name, request.category(), request.description(), request.requiresApproval());
        type.setActive(request.active());
        concessionTypes.saveAndFlush(type);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                FeeAudit.FEE_CONCESSION_TYPE,
                type.getId().toString(),
                changed);

        return FeeConcessionTypeResponse.of(type);
    }
}
