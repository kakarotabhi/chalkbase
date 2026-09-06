package in.chalkbase.student.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>A stable id and a label key — no URL, no route, no component name. The frontend maps ids to its
 * own routes and drops, with a log line, any id it does not know.
 *
 * <p>{@code students} is a container: it has no screen of its own, and it declares both children
 * inline, which is what makes it one. The catalogue drops a container whose children have all been
 * filtered away, so an accountant who may read students but not guardians is shown one entry rather
 * than a section with a gap in it — and this file never has to know which roles a school has
 * invented.
 *
 * <p>Ordered at 25, between the school register (20) and academics (30). Students sit before the
 * academic structure on purpose: the ladder of classes is something a school sets up once and
 * revisits rarely, where the student list is opened every day.
 *
 * <p>The gates are the read permissions, as {@code AcademicsNavigation} uses read gates: both
 * screens are worth opening to look at. The two children are gated separately rather than both on
 * {@code student:student:read}, so that a school which has narrowed guardian access — the one thing
 * the two-resource split in {@code StudentPermissions} exists to allow — does not get a menu entry
 * that opens onto a 403.
 */
@Configuration
public class StudentNavigation {

    /** The students section. A container: it holds no screen of its own. */
    public static final String STUDENTS = "students";

    /** The student list. */
    public static final String STUDENTS_ALL = "students.all";

    /** The guardian directory, which is what makes attaching one father to four children possible. */
    public static final String STUDENTS_GUARDIANS = "students.guardians";

    @Bean
    NavigationProvider studentNavigationProvider() {
        return () -> List.of(new NavigationItem(
                STUDENTS,
                "nav.students",
                "students",
                25,
                null,
                List.of(
                        new NavigationItem(
                                STUDENTS_ALL, "nav.students.all", "students", 10, StudentPermissions.STUDENT_READ),
                        new NavigationItem(
                                STUDENTS_GUARDIANS,
                                "nav.students.guardians",
                                "users",
                                20,
                                StudentPermissions.GUARDIAN_READ))));
    }
}
