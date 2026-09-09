package in.chalkbase.admission.infrastructure;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where this module's screens appear in the menu (ADR-0008).
 *
 * <p>A container with two children, the same shape {@code AttendanceNavigation} and
 * {@code AcademicsNavigation} use: the catalogue drops a container whose children have all been
 * filtered away, so a role holding neither permission never sees an Admissions entry that opens
 * onto nothing. Both children share {@code ENQUIRY_READ} — the queue is a read of the same data the
 * list is, filtered differently, not a separate capability.
 *
 * <p>Ordered at 22, ahead of {@code students} (25): an admission happens before a student record
 * exists, and the menu reads better with the front office's own work above the roster it eventually
 * feeds.
 */
@Configuration
public class AdmissionNavigation {

    public static final String ADMISSIONS = "admissions";
    public static final String ADMISSIONS_ENQUIRIES = "admissions.enquiries";
    public static final String ADMISSIONS_FOLLOW_UPS = "admissions.follow_ups";

    @Bean
    NavigationProvider admissionNavigationProvider() {
        return () -> List.of(new NavigationItem(
                ADMISSIONS,
                "nav.admissions",
                "admissions",
                22,
                null,
                List.of(
                        new NavigationItem(
                                ADMISSIONS_ENQUIRIES,
                                "nav.admissions.enquiries",
                                "admissions",
                                10,
                                AdmissionPermissions.ENQUIRY_READ),
                        new NavigationItem(
                                ADMISSIONS_FOLLOW_UPS,
                                "nav.admissions.follow_ups",
                                "admissions",
                                20,
                                AdmissionPermissions.ENQUIRY_READ))));
    }
}
