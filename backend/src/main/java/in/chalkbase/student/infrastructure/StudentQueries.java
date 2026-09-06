package in.chalkbase.student.infrastructure;

import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentEnrolment;
import in.chalkbase.student.domain.StudentQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Turns a {@link StudentQuery} into a predicate.
 *
 * <p>A specification rather than one JPQL query full of {@code (:param is null or ...)}, for the
 * reason {@code AuditReader} uses one: an absent filter contributes no predicate at all, so the
 * statement PostgreSQL plans is the one {@code idx_student_full_name} and {@code idx_student_status}
 * were created for, instead of one comparing every column to a possibly-null parameter.
 */
public final class StudentQueries {

    private static final char LIKE_ESCAPE = '\\';

    private StudentQueries() {}

    /**
     * @param currentSessionId the year the school says it is in, or null if it has not said. Only
     *     the section filter uses it, and a null here with a section asked for means no student can
     *     match — see {@link #enrolledInSection}.
     */
    public static Specification<Student> matching(StudentQuery query, UUID currentSessionId) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.hasText()) {
                // Lower-cased on both sides rather than ILIKE, so the same query works on any
                // database this ever runs on, and escaped so that a clerk typing an underscore or a
                // per-cent sign — both of which appear in admission numbers — searches for that
                // character instead of for every student.
                String pattern = "%" + escapeForLike(query.q().trim().toLowerCase()) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), pattern, LIKE_ESCAPE),
                        builder.like(builder.lower(root.get("admissionNumber")), pattern, LIKE_ESCAPE)));
            }

            if (query.status() != null) {
                predicates.add(builder.equal(root.get("status"), query.status()));
            }

            if (query.sectionId() != null) {
                predicates.add(enrolledInSection(query.sectionId(), currentSessionId, root, criteria, builder));
            }

            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * An {@code exists} on the enrolment table rather than a join.
     *
     * <p>A join would multiply the student row by its enrolments and turn the page's
     * {@code totalElements} into a count of placements — so a child with four years of history would
     * be four rows of "Class 5B" and the page count would be wrong in a way nobody would trace back
     * to here. {@code exists} asks the question the filter is actually asking.
     *
     * <p>With no current session the predicate is deliberately unsatisfiable rather than widened to
     * "any year". Widening it would answer "who is in 5B" with last year's children as well as this
     * year's, mixed together and indistinguishable, which is worse than an empty list a school fixes
     * by declaring which year it is in.
     */
    private static Predicate enrolledInSection(
            UUID sectionId,
            UUID currentSessionId,
            Root<Student> root,
            jakarta.persistence.criteria.CriteriaQuery<?> criteria,
            jakarta.persistence.criteria.CriteriaBuilder builder) {
        if (currentSessionId == null) {
            return builder.disjunction();
        }
        Subquery<UUID> enrolments = criteria.subquery(UUID.class);
        Root<StudentEnrolment> enrolment = enrolments.from(StudentEnrolment.class);
        enrolments.select(enrolment.get("id"));
        enrolments.where(
                builder.equal(enrolment.get("student").get("id"), root.get("id")),
                builder.equal(enrolment.get("sectionId"), sectionId),
                builder.equal(enrolment.get("academicSessionId"), currentSessionId),
                builder.isTrue(enrolment.get("active")));
        return builder.exists(enrolments);
    }

    /**
     * Escapes the three characters {@code LIKE} treats as syntax.
     *
     * <p>The backslash first, or escaping the wildcards would then escape their escapes. An
     * admission number of the form {@code NORTH/2026/0148} is unaffected; one containing an
     * underscore would otherwise match any character at that position, and a lone {@code %} typed
     * into the box would return the whole school.
     */
    private static String escapeForLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
