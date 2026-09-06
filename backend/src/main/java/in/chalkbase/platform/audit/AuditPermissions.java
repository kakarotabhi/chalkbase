package in.chalkbase.platform.audit;

import in.chalkbase.platform.security.PermissionDefinition;
import in.chalkbase.platform.security.PermissionProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What the audit log lets someone do (ADR-0005, ADR-0018 §6).
 *
 * <p>Registered through the same {@link PermissionProvider} SPI every module uses. The platform
 * declares this one because the audit log is the platform's — every module writes to it, and none
 * of them owns it.
 *
 * <p><strong>One permission, and only a read.</strong> There is no {@code platform:audit:write} and
 * no {@code platform:audit:purge}, because there is no endpoint either. Writing is a side effect of
 * performing an audited action, never something a caller asks for directly; a permission to write
 * an audit row would be a permission to forge one.
 *
 * <p>This is the first permission the shipped {@code AUDITOR} template holds. That template has
 * carried none since it shipped — honest, and useless.
 */
@Configuration
public class AuditPermissions {

    /**
     * Reading this school's audit log. Deliberately powerful: an audit row names who did what to
     * which record, so this is granted for oversight — an inspection, an internal audit — and not
     * as a convenience.
     */
    public static final String AUDIT_READ = "platform:audit:read";

    @Bean
    PermissionProvider auditPermissionProvider() {
        return () -> List.of(new PermissionDefinition(
                AUDIT_READ,
                "platform",
                "View the audit log",
                "Read the record of who did what at this school, and when."));
    }
}
