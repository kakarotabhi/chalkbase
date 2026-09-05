package in.chalkbase.platform.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which schema the current unit of work belongs to.
 *
 * <p>Work with no tenant bound resolves to {@code public} rather than failing: onboarding a school
 * and listing the registry are legitimate operations that belong to no school.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.currentSchemaOrPlatform();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(String tenantId) {
        return TenantContext.PLATFORM.equals(tenantId);
    }
}
