package in.chalkbase.identity.application;

import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.IdentifierType;
import in.chalkbase.identity.domain.IdentityErrorCode;
import in.chalkbase.identity.domain.Role;
import in.chalkbase.identity.domain.TemporaryPasswordGenerator;
import in.chalkbase.identity.domain.UserAccount;
import in.chalkbase.identity.domain.UserCredential;
import in.chalkbase.identity.domain.UserIdentifier;
import in.chalkbase.identity.domain.UserRoleGrant;
import in.chalkbase.identity.infrastructure.RoleRepository;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import in.chalkbase.identity.infrastructure.UserCredentialRepository;
import in.chalkbase.identity.infrastructure.UserIdentifierRepository;
import in.chalkbase.identity.infrastructure.UserRoleGrantRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.security.ScopeType;
import in.chalkbase.platform.tenancy.FirstAdminAccount;
import in.chalkbase.platform.tenancy.FirstAdminProvisioner;
import in.chalkbase.platform.tenancy.TenantContext;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The identity half of ADR-0024: the account a bootstrap creates, once, for a school that has none.
 *
 * <p>Called only from {@code school.application.SchoolService#bootstrap}, through the
 * {@link FirstAdminProvisioner} port — never directly, and never from a controller. The caller has
 * already bound the tenant ({@link TenantContext#callWith}) before invoking this bean, which is
 * what lets {@code @Transactional} below open a Hibernate session against the right schema: the
 * same requirement {@link AuthenticationService} documents for {@link UserAccountService}, and for
 * the same reason — Hibernate picks the tenant when the transaction opens, not per statement.
 *
 * <p>Grants the {@code PRINCIPAL} template at {@code SCHOOL} scope — the same code
 * {@code RoleTemplates} ships and {@code RoleTemplateInstaller} has already copied into this
 * schema. Every other shipped template that could plausibly be "the first administrator" is weaker
 * than this one on purpose (ADR-0005); a school that wants the first sign-in to hold less removes
 * the excess itself, the same way it would edit any other copy of a template.
 */
@Service
public class FirstAdminProvisioningService implements FirstAdminProvisioner {

    /** {@code RoleTemplates}' code for the head-of-school template — a string, because roles are data. */
    private static final String FIRST_ADMIN_ROLE_CODE = "PRINCIPAL";

    private final UserAccountRepository accounts;
    private final UserIdentifierRepository identifiers;
    private final UserCredentialRepository credentials;
    private final UserRoleGrantRepository grants;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public FirstAdminProvisioningService(
            UserAccountRepository accounts,
            UserIdentifierRepository identifiers,
            UserCredentialRepository credentials,
            UserRoleGrantRepository grants,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            AuditService audit) {
        this.accounts = accounts;
        this.identifiers = identifiers;
        this.credentials = credentials;
        this.grants = grants;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The refusal check and every write below share one transaction, so a concurrent second call
     * for the same school either sees the first account and refuses, or is serialised behind it by
     * the database — never both created.
     */
    @Override
    @Transactional
    public FirstAdminAccount provisionFirstAdmin(String schema, String username, String displayName) {
        if (!schema.equals(TenantContext.currentSchemaOrPlatform())) {
            throw new IllegalStateException("provisionFirstAdmin(" + schema + ") called with no matching tenant bound");
        }
        if (accounts.count() > 0) {
            throw new ChalkbaseException(IdentityErrorCode.SCHOOL_ALREADY_BOOTSTRAPPED);
        }
        Role principal = roles.findByCode(FIRST_ADMIN_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Schema " + schema + " has no " + FIRST_ADMIN_ROLE_CODE
                        + " role — SchoolProvisioning must run first"));

        UserAccount account = accounts.saveAndFlush(new UserAccount(displayName.trim()));
        identifiers.saveAndFlush(new UserIdentifier(account, IdentifierType.USERNAME, username.trim()));

        String temporaryPassword = TemporaryPasswordGenerator.generate();
        credentials.saveAndFlush(
                new UserCredential(account, CredentialType.PASSWORD, passwordEncoder.encode(temporaryPassword)));

        grants.saveAndFlush(new UserRoleGrant(account.getId(), principal, ScopeType.SCHOOL, null));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                "USER_ACCOUNT",
                account.getId().toString(),
                List.of("displayName", "username", "role"));

        return new FirstAdminAccount(account.getId(), username.trim(), temporaryPassword);
    }
}
