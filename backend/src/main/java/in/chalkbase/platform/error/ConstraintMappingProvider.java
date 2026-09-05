package in.chalkbase.platform.error;

import java.util.List;

/**
 * Lets a module explain its own database constraints.
 *
 * <p>Without this, the global handler grows a chain of {@code if (message.contains("uq_..."))} —
 * every module editing one method in the platform layer, a permanent merge conflict, and domain
 * knowledge living where it does not belong. Each module implements this instead, and the platform
 * only does the lookup.
 *
 * <p>Register one per module as a {@code @Bean} inside that module.
 */
@FunctionalInterface
public interface ConstraintMappingProvider {

    List<ConstraintMapping> constraintMappings();
}
