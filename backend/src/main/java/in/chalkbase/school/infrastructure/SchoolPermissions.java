package in.chalkbase.school.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Registered the same way {@link SchoolConstraintMappings} registers this module's constraint
 * messages: a {@code @Bean} inside the module, collected by the platform. Add an entry here in the
 * same change that adds the endpoint it guards, and never the other way round — a permission with
 * no enforcement site is a promise the code does not keep.
 *
 * <p>These strings are stored in every school's {@code role_permission} table, so renaming one
 * needs a migration that rewrites those rows.
 */
@Configuration
public class SchoolPermissions {

    /** Reading the school register: which campuses exist, and their board and address details. */
    public static final String SCHOOL_READ = "school:school:read";

    /**
     * Onboarding a campus, which creates its PostgreSQL schema. Deliberately held by no shipped
     * role template: this is a platform-operator action, not something a principal does (ADR-0005).
     */
    public static final String SCHOOL_CREATE = "school:school:create";

    /**
     * Editing this school's own profile — address, contact, principal, affiliation and board.
     *
     * <p>Deliberately not {@link #SCHOOL_CREATE}, and the distinction is the whole point: this is a
     * school administrator correcting their own school's address, not a platform operator bringing
     * a new campus online. It is a school-level action, so unlike {@code create} the shipped role
     * templates should hold it.
     *
     * <p>Held by the {@code PRINCIPAL} and {@code VICE_PRINCIPAL} templates, and enforced by
     * {@code SchoolProfileController}. A school that wants its office administrator to have it adds
     * it to that role itself, which is the whole point of roles being data.
     */
    public static final String SCHOOL_UPDATE = "school:school:update";

    @Bean
    PermissionProvider schoolPermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        SCHOOL_READ, "school", "View schools", "See the schools and campuses on the register."),
                new PermissionDefinition(
                        SCHOOL_CREATE,
                        "school",
                        "Onboard a school",
                        "Register a new campus and bring its database schema online."),
                new PermissionDefinition(
                        SCHOOL_UPDATE,
                        "school",
                        "Edit the school profile",
                        "Change this school's address, contact details, principal and affiliation."));
    }
}
