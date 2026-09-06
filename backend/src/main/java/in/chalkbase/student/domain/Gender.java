package in.chalkbase.student.domain;

/**
 * What {@code ck_student_gender} allows.
 *
 * <p>{@link #OTHER} rather than the government forms' "Transgender", deliberately: the mapping to
 * whatever vocabulary a reporting format wants belongs in the export, not in the record. UDISE+ can
 * change its categories without a migration here, and a child is not asked to pick the label a form
 * happens to use this year.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER
}
