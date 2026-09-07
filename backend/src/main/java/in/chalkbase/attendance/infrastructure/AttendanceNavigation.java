package in.chalkbase.attendance.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>A container with two children, the same shape {@code AcademicsNavigation} uses: the catalogue
 * drops a container whose children have all been filtered away, so a role holding neither
 * permission below never sees an Attendance entry that opens onto nothing.
 *
 * <p>Ordered at 40, just after academics (30) and ahead of the modules that have not shipped yet —
 * for a class teacher this is the screen opened every morning, not configuration.
 */
@Configuration
public class AttendanceNavigation {

    public static final String ATTENDANCE = "attendance";
    public static final String ATTENDANCE_MARK = "attendance.mark";
    public static final String ATTENDANCE_CORRECTIONS = "attendance.corrections";

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
                                ATTENDANCE_CORRECTIONS,
                                "nav.attendance.corrections",
                                "attendance",
                                20,
                                AttendancePermissions.CORRECTION_APPROVE))));
    }
}
