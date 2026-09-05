package in.chalkbase.school.infrastructure;

import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import in.chalkbase.school.domain.SchoolErrorCode;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>Add a row here in the same change that adds the constraint to a migration — the constraint
 * name is the join between the two, and a unique index without an entry here surfaces as a generic
 * "conflicts with information already saved".
 */
@Configuration
public class SchoolConstraintMappings {

    @Bean
    ConstraintMappingProvider schoolConstraintMappingProvider() {
        return () -> List.of(new ConstraintMapping(
                "uq_school_code", SchoolErrorCode.DUPLICATE_CODE, SchoolErrorCode.DUPLICATE_CODE.defaultMessage()));
    }
}
