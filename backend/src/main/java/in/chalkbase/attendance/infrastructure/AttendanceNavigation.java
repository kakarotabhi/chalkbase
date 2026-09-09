package in.chalkbase.attendance.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>A container with three children, the same shape {@code AcademicsNavigation} uses: the
 * catalogue drops a container whose children have all been filtered away, so a role holding none of
 * the permissions below never sees an Attendance entry that opens onto nothing.
 *
 * <p>Ordered at 40, just after academics (30) and ahead of the modules that have not shipped yet —
 * for a class teacher this is the screen opened every morning, not configuration.
 *
 * <p>{@code ATTENDANCE_LEAVE} is gated on {@code LEAVE_READ} rather than {@code LEAVE_REQUEST} or
 * {@code LEAVE_APPROVE}: the same holder-of-read-sees-the-menu-item shape {@code ATTENDANCE_MARK}
 * already uses, and a caller who can only read the queue still needs the item to reach it — filing
 * and deciding are actions the screen itself gates, not the menu.
 */
@Configuration
public class AttendanceNavigation {

    public static final String ATTENDANCE = "attendance";
    public static final String ATTENDANCE_MARK = "attendance.mark";
    public static final String ATTENDANCE_CORRECTIONS = "attendance.corrections";
    public static final String ATTENDANCE_LEAVE = "attendance.leave";

    @Bean
    NavigationProvider attendanceNavigationProvider() {
        return () -> List.of(new NavigationItem(
                ATTENDANCE,
                "nav.attendance",
                "attendance",
                40,
                null,
                List.of(
                        new NavigationItem(
                                ATTENDANCE_MARK,
                                "nav.attendance.mark",
                                "attendance",
                                10,
                                AttendancePermissions.MARK_READ),
                        new NavigationItem(
                                ATTENDANCE_LEAVE,
                                "nav.attendance.leave",
                                "attendance",
                                20,
                                AttendancePermissions.LEAVE_READ),
                        new NavigationItem(
                                ATTENDANCE_CORRECTIONS,
                                "nav.attendance.corrections",
                                "attendance",
                                30,
                                AttendancePermissions.CORRECTION_APPROVE))));
    }
}
