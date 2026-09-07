package in.chalkbase.student.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Two resources, and two actions each, following {@code AcademicsPermissions} — plus one third
 * action on {@code student}, {@link #STUDENT_REVEAL_RESTRICTED}, that ADR-0014 asks for and neither
 * read nor manage can stand in for. The split between read and manage earns its place too: a subject
 * teacher has to know which children are in the class they are teaching and must not be able to
 * change an admission number.
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

    /**
     * Seeing the real value of a Restricted field on a student's record — a caste category, a
     * religion, an EWS/BPL/RTE category, a CWSN/disability status, an allergy, a chronic condition, a
     * medication, a blood group, or an APAAR id (ADR-0014).
     *
     * <p>Deliberately separate from {@link #STUDENT_READ}: ADR-0014 requires a Restricted value to be
     * "masked by default in the UI, revealed by an explicit permission and a recorded action", and a
     * permission that let every reader of the class list decrypt a child's disability status on
     * request would not be that. Holding {@code student:student:manage} does not imply this either —
     * an office can enter a UDISE+ category from a form without ever needing to see what was recorded
     * before, and only asking to <em>see</em> it triggers the read audit
     * ({@code StudentAudit#RESTRICTED_DATA_REVEALED}).
     */
    public static final String STUDENT_REVEAL_RESTRICTED = "student:student:reveal_restricted";

    /**
     * Downloading the roster with every Restricted field included — a child's caste, religion,
     * EWS/BPL/RTE category, CWSN/disability status, allergies, chronic conditions, medication and
     * blood group, in one file, for every student a filter matches (ADR-0014, ADR-0027).
     *
     * <p><strong>Deliberately its own permission, not {@link #STUDENT_REVEAL_RESTRICTED}.</strong>
     * Revealing one field on one child's screen and downloading every Restricted field for every
     * child the school has are different orders of consequence — the file outlives the session, it
     * can be re-shared, and it is the kind of disclosure a school's own data-protection policy would
     * want a named decision behind. Holding {@code reveal_restricted} does not imply this, and
     * holding this does not imply {@code reveal_restricted}: an office that reveals one child's
     * blood group on request need not also be trusted with a bulk file of everyone's.
     *
     * <p><strong>No shipped role template holds this by default</strong> (see
     * {@code RoleTemplates}) — a school grants it deliberately, to whichever role actually needs a
     * bulk Restricted export (a UDISE+ return, typically), the same reasoning that keeps
     * {@code school:school:create} off every template. Every use writes
     * {@code AuditAction#DATA_EXPORTED} naming the fields disclosed and how many rows, in its own
     * transaction — see {@code StudentExportService}.
     */
    public static final String STUDENT_EXPORT_UNMASKED = "student:student:export_unmasked";

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
                        STUDENT_REVEAL_RESTRICTED,
                        "student",
                        "Reveal restricted student data",
                        "See the real value of a caste, religion, category, disability, health or APAAR field"
                                + " that is otherwise masked. Every use is recorded in the audit log."),
                new PermissionDefinition(
                        STUDENT_EXPORT_UNMASKED,
                        "student",
                        "Export unmasked student data",
                        "Download a CSV of students that includes caste, religion, category, disability, health"
                                + " and APAAR fields, not just whether they are recorded. Every export is recorded"
                                + " in the audit log with the fields it disclosed and how many rows."),
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
