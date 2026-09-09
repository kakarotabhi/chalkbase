package in.chalkbase.admission.infrastructure;

import in.chalkbase.admission.domain.Enquiry;
import in.chalkbase.admission.domain.EnquiryQuery;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns an {@link EnquiryQuery} into a predicate.
 *
 * <p>A specification rather than one JPQL query full of {@code (:param is null or ...)}, the same
 * reasoning {@code StudentQueries} gives: an absent filter contributes no predicate at all, so the
 * statement PostgreSQL plans is the one the migration's indexes were built for.
 */
public final class EnquiryQueries {

    private static final char LIKE_ESCAPE = '\\';

    private EnquiryQueries() {}

    public static Specification<Enquiry> matching(EnquiryQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.hasText()) {
                // Lower-cased on both sides rather than ILIKE, so the same query works on any
                // database this ever runs on, and escaped so a front-office clerk typing a phone
                // number with no special meaning to LIKE still gets an exact search.
                String pattern = "%" + escapeForLike(query.q().trim().toLowerCase()) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("childFullName")), pattern, LIKE_ESCAPE),
                        builder.like(builder.lower(root.get("parentName")), pattern, LIKE_ESCAPE),
                        builder.like(builder.lower(root.get("parentPhone")), pattern, LIKE_ESCAPE)));
            }

            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }

            if (query.source() != null) {
                predicates.add(builder.equal(root.get("source"), query.source()));
            }

            if (query.assignedCounsellorId() != null) {
                predicates.add(builder.equal(root.get("assignedCounsellorId"), query.assignedCounsellorId()));
            }

            if (query.interestedClassId() != null) {
                predicates.add(builder.equal(root.get("interestedClassId"), query.interestedClassId()));
            }

            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Escapes the three characters {@code LIKE} treats as syntax. See {@code StudentQueries} for the same rule. */
    private static String escapeForLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
