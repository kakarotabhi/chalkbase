package in.chalkbase.fee.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Three resources, following {@code AcademicsPermissions}' shape exactly: a fee head, a
 * concession type and a fee structure are separately gated because a school's real split of "who
 * may see the price list" from "who may change it" from "who may decide what counts as a valid
 * concession" genuinely differs by school — the accountant runs the day-to-day price list and
 * structure but, in this build's own role templates, does not define what a concession type means
 * for the school; see {@code RoleTemplates}' own Javadoc for the argument.
 *
 * <p>These strings are stored in every school's {@code role_permission} table, so renaming one
 * needs a migration that rewrites those rows.
 */
@Configuration
public class FeePermissions {

    /** Seeing the school's catalogue of fee heads. */
    public static final String HEAD_READ = "fee:head:read";

    /** Adding, renaming, recategorising, capping and retiring fee heads. */
    public static final String HEAD_MANAGE = "fee:head:manage";

    /** Seeing the school's catalogue of concession types. */
    public static final String CONCESSION_TYPE_READ = "fee:concession_type:read";

    /** Adding, renaming and retiring concession types. Applying one to a student is not built (see the module Javadoc). */
    public static final String CONCESSION_TYPE_MANAGE = "fee:concession_type:manage";

    /** Seeing a class's fee structure for a session. */
    public static final String STRUCTURE_READ = "fee:structure:read";

    /** Writing a new version of a class's fee structure, and copying one from a previous session. */
    public static final String STRUCTURE_MANAGE = "fee:structure:manage";

    @Bean
    PermissionProvider feePermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        HEAD_READ, "fee", "View fee heads", "See the school's catalogue of fee heads."),
                new PermissionDefinition(
                        HEAD_MANAGE,
                        "fee",
                        "Manage fee heads",
                        "Add, rename, recategorise, cap and deactivate fee heads."),
                new PermissionDefinition(
                        CONCESSION_TYPE_READ,
                        "fee",
                        "View concession types",
                        "See the school's catalogue of concession and waiver types."),
                new PermissionDefinition(
                        CONCESSION_TYPE_MANAGE,
                        "fee",
                        "Manage concession types",
                        "Add, rename and deactivate concession and waiver types."),
                new PermissionDefinition(
                        STRUCTURE_READ,
                        "fee",
                        "View fee structures",
                        "See what a class is charged for an academic session."),
                new PermissionDefinition(
                        STRUCTURE_MANAGE,
                        "fee",
                        "Manage fee structures",
                        "Write a new version of a class's fee structure, and copy one from a previous session."));
    }
}
