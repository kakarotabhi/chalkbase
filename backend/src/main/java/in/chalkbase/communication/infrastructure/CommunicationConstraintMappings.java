package in.chalkbase.communication.infrastructure;

import in.chalkbase.communication.domain.CommunicationErrorCode;
import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>{@code uq_circular_target_whole_class} and {@code uq_circular_target_section} are two partial
 * unique indexes over the same underlying conflict — "this class or section is already targeted by
 * this circular" — so both map to the one error code a caller cannot otherwise tell apart.
 */
@Configuration
public class CommunicationConstraintMappings {

    @Bean
    ConstraintMappingProvider communicationConstraintMappingProvider() {
        return () -> List.of(
                mapping("uq_circular_target_whole_class", CommunicationErrorCode.DUPLICATE_TARGET),
                mapping("uq_circular_target_section", CommunicationErrorCode.DUPLICATE_TARGET));
    }

    private static ConstraintMapping mapping(String constraintName, CommunicationErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
