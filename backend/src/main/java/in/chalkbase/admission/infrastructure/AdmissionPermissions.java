package in.chalkbase.admission.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>Two permissions, the same shape {@code AttendancePermissions} chose for the same reason:
 * capturing an enquiry, assigning or reassigning its counsellor, and logging a follow-up are all
 * the same act — moving an enquiry forward — so one {@code manage} permission covers all three
 * rather than three that would almost always be granted together anyway. There is no
 * scope-narrower-than-school grant yet, the same honesty {@code AttendancePermissions} states for
 * {@code MARK_MANAGE}: a counsellor holding {@code ENQUIRY_MANAGE} can reassign or follow up any
 * enquiry in the school, not only their own, because nothing in the model resolves "my own
 * enquiries" as an enforceable scope today. The due-date queue defaults to "mine" in the read model
 * ({@code AdmissionEnquiryService.dueFollowUps}), which is a convenience for the common case, not
 * an authorization boundary.
 */
@Configuration
public class AdmissionPermissions {

    /** Seeing enquiries: the list, one enquiry's detail and follow-up history, and the due-date queue. */
    public static final String ENQUIRY_READ = "admission:enquiry:read";

    /** Capturing an enquiry, assigning or reassigning its counsellor, and logging a follow-up. */
    public static final String ENQUIRY_MANAGE = "admission:enquiry:manage";

    @Bean
    PermissionProvider admissionPermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        ENQUIRY_READ,
                        "admission",
                        "View enquiries",
                        "See the enquiry list, one enquiry's detail and follow-up history, and the due-date"
                                + " follow-up queue."),
                new PermissionDefinition(
                        ENQUIRY_MANAGE,
                        "admission",
                        "Manage enquiries",
                        "Capture a new enquiry, assign or reassign its counsellor, and log a follow-up."));
    }
}
