package in.chalkbase.academics.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Two resources and two actions each, rather than one {@code academics:*} permission or four
 * verbs per resource. The split that earns its place is read from manage: a subject teacher needs
 * to know which classes and sections exist to do anything at all, and must not be able to rename
 * one. Splitting {@code manage} further into create, update and reorder would be three permissions
 * a school has no reason to hold separately — nobody may add a class but not rename it.
 *
 * <p>Sessions and classes are separate resources because their audiences genuinely differ: an
 * admission counsellor reads both, and the person who declares that the school has moved into
 * 2027-28 is not necessarily the person who adds a section to Class 5.
 *
 * <p>These strings are stored in every school's {@code role_permission} table, so renaming one
 * needs a migration that rewrites those rows.
 */
@Configuration
public class AcademicsPermissions {

    /** Seeing the school's academic years and which one it is in. */
    public static final String SESSION_READ = "academics:session:read";

    /** Creating and editing academic years, and declaring which one the school is in. */
    public static final String SESSION_MANAGE = "academics:session:manage";

    /** Seeing the ladder of classes and their sections. Needed by anything that names a class. */
    public static final String CLASS_READ = "academics:class:read";

    /** Adding, renaming, reordering, retiring and reinstating classes and sections. */
    public static final String CLASS_MANAGE = "academics:class:manage";

    @Bean
    PermissionProvider academicsPermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        SESSION_READ,
                        "academics",
                        "View academic sessions",
                        "See the school's academic years and which one it is currently in."),
                new PermissionDefinition(
                        SESSION_MANAGE,
                        "academics",
                        "Manage academic sessions",
                        "Add and edit academic years, and move the school into one."),
                new PermissionDefinition(
                        CLASS_READ,
                        "academics",
                        "View classes and sections",
                        "See the school's classes and the sections inside them."),
                new PermissionDefinition(
                        CLASS_MANAGE,
                        "academics",
                        "Manage classes and sections",
                        "Add, rename, reorder and deactivate classes and their sections."));
    }
}
