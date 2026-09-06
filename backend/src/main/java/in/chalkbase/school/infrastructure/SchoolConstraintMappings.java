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
        return () -> List.of(
                mapping("uq_school_code", SchoolErrorCode.DUPLICATE_CODE),

                // school_profile. Every check constraint on that table is claimed here, including
                // the ones the request DTO already validates: the DTO speaks for HTTP clients, and
                // these are what a violation from anywhere else is called.
                mapping("uq_school_profile_singleton", SchoolErrorCode.PROFILE_ALREADY_EXISTS),
                mapping("ck_school_profile_singleton", SchoolErrorCode.PROFILE_ALREADY_EXISTS),
                mapping("ck_school_profile_pincode", SchoolErrorCode.INVALID_PINCODE),
                mapping("ck_school_profile_email", SchoolErrorCode.INVALID_CONTACT),
                mapping("ck_school_profile_phone", SchoolErrorCode.INVALID_CONTACT),
                mapping("ck_school_profile_website", SchoolErrorCode.INVALID_CONTACT),
                mapping("ck_school_profile_board", SchoolErrorCode.UNKNOWN_BOARD));
    }

    /** The common case: the error code's own sentence is the one to show. */
    private static ConstraintMapping mapping(String constraintName, SchoolErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
