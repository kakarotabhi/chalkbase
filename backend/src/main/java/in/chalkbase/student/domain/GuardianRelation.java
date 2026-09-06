package in.chalkbase.student.domain;

/**
 * How a guardian stands to a student. What {@code ck_student_guardian_relation} allows.
 *
 * <p>On the <em>link</em>, not on the guardian, and that is the point of ADR-0020 §5: one person is
 * "father" to one child and nothing at all to another, so the relationship belongs to the pair.
 * Putting it on {@code guardian} would force a second copy of the person for every child, which is
 * the shape this module exists to avoid.
 *
 * <p>{@link #LOCAL_GUARDIAN} is a distinct entry rather than a flavour of {@link #GUARDIAN} because
 * Indian schools genuinely treat it as one: the person in the city who can be rung and can collect
 * the child, where the parents are elsewhere. A boarding school's forms have a box for it.
 */
public enum GuardianRelation {
    FATHER,
    MOTHER,
    GUARDIAN,
    LOCAL_GUARDIAN,
    OTHER
}
