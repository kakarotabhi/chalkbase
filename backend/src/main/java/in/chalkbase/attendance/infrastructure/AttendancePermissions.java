package in.chalkbase.attendance.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Three permissions rather than a wider split by grain, because the grain is a per-school
 * setting (ADR-0006) and not a thing a role holder chooses between — a class teacher marks
 * whichever grain their school runs. {@code MARK_MANAGE} covers marking a section within its edit
 * window and filing a correction request once it has locked: both are the same act, "record what
 * happened", and only the clock — not the permission — decides which path applies.
 * {@code CORRECTION_APPROVE} is separate and deliberately narrower, the same way
 * {@code student:student:reveal_restricted} is: deciding that a teacher's memory of a day overrides
 * what was recorded at the time is oversight, not marking.
 *
 * <p>There is no section-scoped narrowing yet — {@code ScopeType.SECTION} exists on
 * {@code AccessScope} but nothing in the codebase resolves it into a query filter, the same gap
 * {@code RoleTemplates} already notes for the guardian permission on {@code CLASS_TEACHER}. So
 * {@code MARK_MANAGE} in this build reaches every section in the school, not only the holder's own
 * — honest about what the model can currently express, not a regression this module introduced.
 */
@Configuration
public class AttendancePermissions {

    /** Seeing marked attendance: a section on a date, or a student's history. */
    public static final String MARK_READ = "attendance:mark:read";

    /** Marking or editing attendance inside its edit window, and filing a correction once locked. */
    public static final String MARK_MANAGE = "attendance:mark:manage";

    /** Approving or rejecting a correction request. */
    public static final String CORRECTION_APPROVE = "attendance:correction:approve";

    @Bean
    PermissionProvider attendancePermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        MARK_READ,
                        "attendance",
                        "View attendance",
                        "See a section's attendance for a date, and a student's attendance history."),
                new PermissionDefinition(
                        MARK_MANAGE,
                        "attendance",
                        "Mark attendance",
                        "Mark and edit attendance while it is still within its edit window, and file a"
                                + " correction request once it has locked."),
                new PermissionDefinition(
                        CORRECTION_APPROVE,
                        "attendance",
                        "Approve attendance corrections",
                        "Approve or reject a teacher's request to change a locked attendance mark."));
    }
}
