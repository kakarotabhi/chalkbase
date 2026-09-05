package in.chalkbase.platform.security;

import java.util.List;

/**
 * Lets a module declare the permissions it enforces.
 *
 * <p>Mirrors {@code platform.error.ConstraintMappingProvider}: the platform owns the registry and
 * the lookup, each module owns its own entries. Without this the catalogue becomes one enum in the
 * shared kernel that all sixteen modules edit — a permanent merge conflict, and domain knowledge
 * living where it does not belong.
 *
 * <p>Register one per module as a {@code @Bean} inside that module. Permissions are code; roles are
 * data (ADR-0005), so this interface is the only way a permission comes into existence.
 */
@FunctionalInterface
public interface PermissionProvider {

    List<PermissionDefinition> permissions();
}
