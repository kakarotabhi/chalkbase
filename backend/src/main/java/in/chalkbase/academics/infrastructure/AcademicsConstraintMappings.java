package in.chalkbase.academics.infrastructure;

import in.chalkbase.academics.domain.AcademicsErrorCode;
import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>Every constraint a user can reach is claimed here, including the ones a request DTO already
 * validates. The DTO speaks for HTTP clients; these are what the violation is called when something
 * writes to the table without going through the API, and an unclaimed constraint surfaces as a
 * generic "conflicts with information already saved".
 *
 * <p>{@code uq_academic_session_one_current} is a partial unique index rather than a table
 * constraint, and PostgreSQL reports the index name in exactly the same place — so it is claimed
 * the same way. Reaching it through the API should be impossible: making a session current clears
 * the previous one first, in the same transaction.
 */
@Configuration
public class AcademicsConstraintMappings {

    @Bean
    ConstraintMappingProvider academicsConstraintMappingProvider() {
        return () -> List.of(
                mapping("uq_academic_session_name", AcademicsErrorCode.DUPLICATE_SESSION_NAME),
                mapping("uq_academic_session_one_current", AcademicsErrorCode.SESSION_ALREADY_CURRENT),
                mapping("ck_academic_session_dates", AcademicsErrorCode.INVALID_SESSION_DATES),
                mapping("uq_school_class_name", AcademicsErrorCode.DUPLICATE_CLASS_NAME),
                mapping("uq_school_class_sequence", AcademicsErrorCode.CLASS_SEQUENCE_TAKEN),
                mapping("uq_section_name_in_class", AcademicsErrorCode.DUPLICATE_SECTION_NAME));
    }

    /** The common case: the error code's own sentence is the one to show. */
    private static ConstraintMapping mapping(String constraintName, AcademicsErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
