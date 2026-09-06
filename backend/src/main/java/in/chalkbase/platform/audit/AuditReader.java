package in.chalkbase.platform.audit;

import in.chalkbase.platform.api.PageResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading the audit log. The only thing anyone may do to it through the API.
 *
 * <p><strong>There is no write side here and there must never be one</strong> — no update, no
 * delete, no "clear the log" for an administrator having a bad day (ADR-0018 §6). Rows arrive
 * through {@link AuditService} from the code that performs the audited action, and they leave
 * through a scheduled retention purge, which is a platform job and not an endpoint.
 *
 * <p>Tenant-scoped like everything else: no school argument, because the schema is the boundary
 * (ADR-0011). The transaction lives here rather than on the controller, so Hibernate opens its
 * session after {@code SessionTenantFilter} has bound the school.
 */
@Service
@Transactional(readOnly = true)
public class AuditReader {

    private final AuditEventRepository events;

    public AuditReader(AuditEventRepository events) {
        this.events = events;
    }

    /** One page of this school's audit log, in the order the caller asked for. */
    public PageResponse<AuditEventResponse> search(AuditQuery query, Pageable pageable) {
        Page<AuditEvent> page = events.findAll(matching(query), pageable);
        List<AuditEventResponse> content =
                page.getContent().stream().map(AuditEventResponse::of).toList();
        return PageResponse.of(page, content);
    }

    /**
     * Builds the predicate from whichever filters were supplied.
     *
     * <p>A specification rather than one JPQL query full of {@code (:param is null or ...)}: an
     * absent filter contributes no predicate at all, so the statement PostgreSQL plans is the one
     * the indexes in {@code V2026_09_06_0900__platform_create_audit_event.sql} were built for,
     * instead of one where every column is compared to a possibly-null parameter.
     */
    private static Specification<AuditEvent> matching(AuditQuery query) {
        return (root, criteria, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (query.actorId() != null) {
                predicates.add(builder.equal(root.get("actorId"), query.actorId()));
            }
            if (query.action() != null && !query.action().isBlank()) {
                predicates.add(builder.equal(root.get("action"), query.action()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThan(root.get("occurredAt"), query.to()));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
