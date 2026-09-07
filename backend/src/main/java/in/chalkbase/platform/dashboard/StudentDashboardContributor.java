package in.chalkbase.platform.dashboard;

import java.util.Optional;
import java.util.Set;

/**
 * Student's contribution to the landing dashboard: enrolment by class, and the two linkage gaps.
 *
 * <p>Mirrors {@link AcademicsDashboardContributor} and, through it,
 * {@code platform.navigation.NavigationProvider} — the platform owns the aggregation, {@code
 * student} owns what belongs in its own tiles and whether the caller may see them, and that is
 * what keeps {@code platform.dashboard} from importing {@code student.api} directly. The shared
 * kernel must not import a feature module.
 *
 * <p>Register one implementation as a {@code @Bean} inside {@code student}.
 */
public interface StudentDashboardContributor {

    /**
     * @param heldPermissions the caller's granted authorities. The implementation decides using its
     *     own {@code StudentPermissions.STUDENT_READ}.
     * @return empty when the caller lacks the permission, or when no academic session is current —
     *     "enrolled" has no meaning without a year to enrol into (ADR-0019).
     */
    Optional<StudentsTile> studentsTile(Set<String> heldPermissions);

    /**
     * @param heldPermissions the caller's granted authorities. The implementation checks its own
     *     {@code StudentPermissions.STUDENT_READ} and {@code GUARDIAN_READ} independently, one per
     *     field of {@link LinkageGapsTile} — a school that narrowed guardian access does not lose
     *     the student half along with it.
     * @return empty only when the caller holds neither permission. Holding just one still returns a
     *     tile with the other field absent.
     */
    Optional<LinkageGapsTile> linkageGapsTile(Set<String> heldPermissions);
}
