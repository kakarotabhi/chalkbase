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
