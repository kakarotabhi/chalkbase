package in.chalkbase.identity.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The twelve roles Chalkbase ships (ADR-0005).
 *
 * <p>These are the archetypes every Indian school recognises, and they are only ever a starting
 * point: no two schools split the work the same way, so a school copies one and then edits it. One
 * school lets the accountant approve concessions; the next insists only the principal may. Neither
 * of those is a code change.
 *
 * <p><strong>The permission sets are deliberately thin, because the catalogue is.</strong> A
 * template may only hold permissions that exist in this build — {@code RoleTemplateInstaller}
 * checks that against the {@code PermissionCatalog} at startup and refuses to start otherwise. As
 * modules land, the templates grow here and each school's administrator is shown a "new permissions
 * available" review prompt rather than having them applied silently.
 *
 * <p>Permission codes appear as string literals rather than as constants imported from the owning
 * module: a module's constants live behind its own boundary, and these strings are the published
 * contract anyway. The startup check is what catches a typo.
 */
public final class RoleTemplates {

    private static final String SCHOOL_READ = "school:school:read";
    private static final String SCHOOL_UPDATE = "school:school:update";
    private static final String SESSION_READ = "academics:session:read";
    private static final String SESSION_MANAGE = "academics:session:manage";
    private static final String CLASS_READ = "academics:class:read";
    private static final String CLASS_MANAGE = "academics:class:manage";
    private static final String USER_READ = "identity:user:read";
    private static final String ROLE_MANAGE = "identity:role:manage";
    private static final String AUDIT_READ = "platform:audit:read";

    /**
     * Note what no template holds: {@code school:school:create}. Onboarding a campus creates a
     * database schema and is a platform-operator action, not a school one (ADR-0005).
     *
     * <p>{@code school:school:update} is the other half of that distinction and two templates do
     * hold it: correcting your own school's address is school work, and the head of the school is
     * who does it.
     *
     * <p>The academics permissions follow the same split, and the read half is wider than any
     * shipped permission has been so far. A class teacher, a subject teacher and an admission
     * counsellor all have to know which classes, sections and academic years exist before they can
     * do anything at all — a counsellor cannot take an application for a class the screen will not
     * show them. Changing the ladder or moving the school into a new academic year is a decision
     * about the school, so only the two templates that run it hold {@code manage}. A school that
     * wants its office administrator to add sections adds the permission to that role itself, which
     * is the whole point of roles being data.
     */
    private static final List<RoleTemplate> TEMPLATES = List.of(
            new RoleTemplate(
                    "PRINCIPAL",
                    "Principal",
                    "Head of the school. Sees everything and decides who else may.",
                    SCHOOL_READ,
                    SCHOOL_UPDATE,
                    SESSION_READ,
                    SESSION_MANAGE,
                    CLASS_READ,
                    CLASS_MANAGE,
                    USER_READ,
                    ROLE_MANAGE),
            new RoleTemplate(
                    "VICE_PRINCIPAL",
                    "Vice Principal",
                    "Deputises for the principal on the academic side, without control of access.",
                    SCHOOL_READ,
                    SCHOOL_UPDATE,
                    SESSION_READ,
                    SESSION_MANAGE,
                    CLASS_READ,
                    CLASS_MANAGE,
                    USER_READ),
            new RoleTemplate(
                    "CLASS_TEACHER",
                    "Class Teacher",
                    "Owns one section: its attendance, its results and its parents.",
                    SCHOOL_READ,
                    SESSION_READ,
                    CLASS_READ),
            new RoleTemplate(
                    "SUBJECT_TEACHER",
                    "Subject Teacher",
                    "Teaches one subject across several sections.",
                    SCHOOL_READ,
                    SESSION_READ,
                    CLASS_READ),
            new RoleTemplate("ACCOUNTANT", "Accountant", "Fees, receipts and the day book.", SCHOOL_READ),
            new RoleTemplate(
                    "ADMISSION_COUNSELLOR",
                    "Admission Counsellor",
                    "Enquiries and applications, from the first call to the admission.",
                    SCHOOL_READ,
                    SESSION_READ,
                    CLASS_READ),
            new RoleTemplate("LIBRARIAN", "Librarian", "The catalogue, issues, returns and fines.", SCHOOL_READ),
            new RoleTemplate(
                    "TRANSPORT_MANAGER",
                    "Transport Manager",
                    "Routes, stops, vehicles and the drivers who run them.",
                    SCHOOL_READ),
            new RoleTemplate("HOSTEL_WARDEN", "Hostel Warden", "Rooms, allotments and the mess.", SCHOOL_READ),
            // Parent and student hold nothing yet, and that is honest rather than an omission: what
            // they may see is their own child's or their own record, and no module that owns such a
            // record has shipped. Their reach is derived from the guardian-of relationship and the
            // SELF scope, never from an administrator assigning them a class (ADR-0005).
            new RoleTemplate("PARENT", "Parent", "A guardian, who sees their own children and nothing else."),
            new RoleTemplate("STUDENT", "Student", "A student, who sees their own record and nothing else."),
            // The audit log is the auditor's, and until now this template held nothing at all —
            // honest, and useless (ADR-0018). platform:audit:read is deliberately on this template
            // only: reading who did what to which record is oversight, not a convenience, and a
            // school that wants its principal to have it adds it to that role itself.
            new RoleTemplate(
                    "AUDITOR",
                    "Auditor",
                    "Read-only oversight for an inspection or an internal audit. Changes nothing.",
                    SCHOOL_READ,
                    USER_READ,
                    AUDIT_READ));

    private RoleTemplates() {}

    public static List<RoleTemplate> all() {
        return TEMPLATES;
    }

    /** Every permission code mentioned by any template, for the startup check against the catalogue. */
    public static Set<String> referencedPermissions() {
        return TEMPLATES.stream().flatMap(t -> t.permissions().stream()).collect(Collectors.toUnmodifiableSet());
    }
}
