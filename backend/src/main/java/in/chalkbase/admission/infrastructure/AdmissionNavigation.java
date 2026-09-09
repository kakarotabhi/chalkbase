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
 * <p>Ordered at 45, just after {@code attendance} (40) and well ahead of {@code settings} (90) —
 * a class teacher's daily work and a front office's daily work sit together, above configuration
 * screens. Deliberately not placed ahead of {@code students} (25) despite an admission happening
 * before a student record exists: {@code students}, {@code academics} and {@code attendance} are
 * pinned by {@code MeApiTests} at exact array positions, and slotting in after all three rather
 * than before any of them is one line of new assertions there instead of a rewrite of existing
 * ones for no functional gain.
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
                45,
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
