package in.chalkbase.student.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Two resources and two actions each, following {@code AcademicsPermissions}. The split that
 * earns its place is read from manage: a subject teacher has to know which children are in the class
 * they are teaching and must not be able to change an admission number.
 *
 * <p><strong>Students and guardians are separate resources, and that separation is the one worth
 * arguing.</strong> They cover the same families, so a single {@code student:*} pair would be
 * simpler — but the guardian directory is a searchable list of adults with phone numbers, and it is
 * used by a smaller group than the class list is. Keeping them apart is what lets a school give a
 * subject teacher the roster of the children they teach without also giving them a searchable
 * directory of eight hundred parents' mobile numbers. The shipped templates do not draw that line
 * (see {@code RoleTemplates}); the point is that a school can, without a code change.
 *
 * <p>Splitting {@code manage} further — admit, edit, enrol, withdraw — would be four permissions no
 * school has a reason to hold separately: nobody may admit a child but not place them in a class.
 *
 * <p>These strings are stored in every school's {@code role_permission} table, so renaming one needs
 * a migration that rewrites those rows.
 */
@Configuration
public class StudentPermissions {

    /** Seeing the student list and a child's record. */
    public static final String STUDENT_READ = "student:student:read";

    /** Admitting a child, correcting their record, and placing them in a class. */
    public static final String STUDENT_MANAGE = "student:student:manage";

    /** Seeing the guardian directory and the guardians on a child's record. */
    public static final String GUARDIAN_READ = "student:guardian:read";

    /** Adding and correcting guardians, and attaching or detaching them from a child. */
    public static final String GUARDIAN_MANAGE = "student:guardian:manage";

    @Bean
    PermissionProvider studentPermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        STUDENT_READ, "student", "View students", "See the school's students and each child's record."),
                new PermissionDefinition(
                        STUDENT_MANAGE,
                        "student",
                        "Manage students",
                        "Admit students, correct their records, and enrol them in a class and section."),
                new PermissionDefinition(
                        GUARDIAN_READ,
                        "student",
                        "View guardians",
                        "See the school's guardians and their contact details."),
                new PermissionDefinition(
                        GUARDIAN_MANAGE,
                        "student",
                        "Manage guardians",
                        "Add and edit guardians, and attach or detach them from a student."));
    }
}
