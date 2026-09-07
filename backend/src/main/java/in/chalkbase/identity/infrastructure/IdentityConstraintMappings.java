package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>{@code uq_role_code} is claimed mostly defensively: {@code RoleManagementService} derives a
 * role's code from its name and resolves a collision against the codes it already knows about
 * before saving, so a client should rarely reach this one — it exists for the race between two
 * admins creating similarly named roles at the same instant, which a read-then-write cannot close
 * on its own. {@code uq_user_role_grant} has no such pre-check at all: granting the same role at the
 * same scope twice is simply refused by the database, and this is what that refusal is called
 * rather than a generic {@code CONF_001}.
 *
 * <p>No message here contains a value (ADR-0014). Not a username, not a display name.
 */
@Configuration
public class IdentityConstraintMappings {

    @Bean
    ConstraintMappingProvider identityConstraintMappingProvider() {
        return () -> List.of(
                mapping("uq_user_identifier_value", IdentityErrorCode.USERNAME_TAKEN),
                mapping("uq_role_code", IdentityErrorCode.ROLE_NAME_TAKEN),
                mapping("uq_user_role_grant", IdentityErrorCode.GRANT_ALREADY_EXISTS));
    }

    private static ConstraintMapping mapping(String constraintName, IdentityErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
