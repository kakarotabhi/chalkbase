package in.chalkbase.communication.infrastructure;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module lets someone do (ADR-0005).
 *
 * <p>{@code CIRCULAR_READ} is deliberately the gate on the target-preview endpoint too — the
 * screen that answers "how many students would this reach" before a circular is published. That
 * count is Confidential-adjacent under ADR-0014 the moment targeting could name anything more than
 * a class or section (fee status, attendance status), and the module map's own trap for this
 * module names the failure directly: a targeting query must never be reachable by holding some
 * other module's permission. So this stays {@code communication}'s own permission even though
 * today it only ever resolves a class/section roster through {@code student.api.StudentLookup} —
 * the boundary is drawn now, before there is a second, more sensitive dimension to target by.
 *
 * <p>{@code CIRCULAR_ACKNOWLEDGE} is separate from {@code CIRCULAR_MANAGE} for the same reason
 * {@code attendance:correction:approve} is separate from marking: recording that a family
 * acknowledged a circular is a different act from composing and publishing one, and a school may
 * want its class teachers to do the first without the second.
 */
@Configuration
public class CommunicationPermissions {

    /** Reading circulars, their targets and their per-recipient status, and the target-preview count. */
    public static final String CIRCULAR_READ = "communication:circular:read";

    /** Composing a circular, adding its targets, and publishing it. */
    public static final String CIRCULAR_MANAGE = "communication:circular:manage";

    /** Recording that a recipient's family acknowledged a circular. */
    public static final String CIRCULAR_ACKNOWLEDGE = "communication:circular:acknowledge";

    @Bean
    PermissionProvider communicationPermissionProvider() {
        return () -> List.of(
                new PermissionDefinition(
                        CIRCULAR_READ,
                        "communication",
                        "View circulars",
                        "See circulars, who they were targeted at, and each recipient's delivery and"
                                + " acknowledgement status — including the recipient count shown while composing"
                                + " one."),
                new PermissionDefinition(
                        CIRCULAR_MANAGE,
                        "communication",
                        "Compose and publish circulars",
                        "Compose a circular, target it by class or section, and publish it."),
                new PermissionDefinition(
                        CIRCULAR_ACKNOWLEDGE,
                        "communication",
                        "Record circular acknowledgements",
                        "Record that a recipient's family has acknowledged a circular that requires one."));
    }
}
