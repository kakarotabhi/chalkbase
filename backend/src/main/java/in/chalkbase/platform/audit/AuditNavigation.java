package in.chalkbase.platform.audit;

import in.chalkbase.platform.navigation.NavigationItem;
import in.chalkbase.platform.navigation.NavigationProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where the audit log appears in the menu (ADR-0008).
 *
 * <p>A stable id and a label key — no URL, no route, no component name. The frontend maps ids to
 * its own routes and drops, with a log line, any id it does not know.
 *
 * <p><strong>The frontend does not know this id yet, so it will be dropped client-side.</strong>
 * That is the designed behaviour of ADR-0008 rather than a bug: the two sides ship independently,
 * and an id the frontend cannot resolve costs a log line instead of a broken menu entry. The entry
 * starts working the moment the route registry learns the id, with no backend change.
 *
 * <p>Gated on {@code platform:audit:read}, which is a convenience and never the control — the
 * endpoint enforces the same permission, and that enforcement is what protects the data.
 */
@Configuration
public class AuditNavigation {

    /** The audit log screen. Ordered late: it is oversight, not daily work. */
    public static final String AUDIT = "audit";

    @Bean
    NavigationProvider auditNavigationProvider() {
        return () -> List.of(new NavigationItem(AUDIT, "nav.audit", "shield-check", 95, AuditPermissions.AUDIT_READ));
    }
}
