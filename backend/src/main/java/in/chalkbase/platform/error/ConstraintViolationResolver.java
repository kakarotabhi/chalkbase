package in.chalkbase.platform.error;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Turns a database integrity violation into a message that means something to a user.
 *
 * <p>The constraint name is read from Hibernate's {@code ConstraintViolationException}, not by
 * searching the driver's message text. Message text varies between driver versions and server
 * locales, so matching on it is a test that passes today and fails after an upgrade.
 */
@Component
public class ConstraintViolationResolver {

    private static final Logger log = LoggerFactory.getLogger(ConstraintViolationResolver.class);

    private final Map<String, ConstraintMapping> byConstraintName = new HashMap<>();

    public ConstraintViolationResolver(List<ConstraintMappingProvider> providers) {
        for (ConstraintMappingProvider provider : providers) {
            for (ConstraintMapping mapping : provider.constraintMappings()) {
                ConstraintMapping previous =
                        byConstraintName.put(mapping.constraintName().toLowerCase(), mapping);
                if (previous != null) {
                    throw new IllegalStateException("Two modules claim constraint " + mapping.constraintName());
                }
            }
        }
        log.info("Registered {} database constraint messages", byConstraintName.size());
    }

    /** The mapping for this violation, or empty when no module has claimed the constraint. */
    public Optional<ConstraintMapping> resolve(DataIntegrityViolationException exception) {
        return constraintNameOf(exception).map(name -> byConstraintName.get(name.toLowerCase()));
    }

    /**
     * The name of the constraint that was violated, whether or not any module has claimed it.
     *
     * <p>Exposed so an unmapped violation can be logged by <em>name</em> rather than by exception.
     * PostgreSQL puts its {@code DETAIL} line inside the exception message — {@code Key
     * (admission_number)=(2026/0001) already exists} — so logging the exception logs the offending
     * value, which for this product is a child's admission number (ADR-0014).
     */
    public Optional<String> constraintName(DataIntegrityViolationException exception) {
        return constraintNameOf(exception);
    }

    private Optional<String> constraintNameOf(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException hibernate) {
                return Optional.ofNullable(hibernate.getConstraintName());
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return Optional.empty();
    }
}
