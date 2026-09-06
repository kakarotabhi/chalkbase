package in.chalkbase.student.infrastructure;

import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import in.chalkbase.student.domain.StudentErrorCode;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>Every constraint a user can reach is claimed here, including the ones a request DTO already
 * validates. The DTO speaks for HTTP clients; these are what the violation is called when something
 * writes to the table without going through the API, and an unclaimed constraint surfaces as a
 * generic "conflicts with information already saved" — which, on the admissions screen, means a
 * clerk retyping a whole form to find out that the admission number was the problem.
 *
 * <p>Two of these are <strong>partial unique indexes</strong> rather than table constraints —
 * {@code uq_student_enrolment_one_active} and {@code uq_student_guardian_one_primary}. PostgreSQL
 * reports an index name in exactly the same place, so they are claimed the same way. Reaching either
 * through the API should be impossible: the services check first, and clear-then-flush before
 * setting. These are what the race between two clerks pressing Save at the same instant is called.
 *
 * <p><strong>No message here contains a value</strong> (ADR-0014). Not the admission number, not the
 * roll number, not a name. A constraint message is a response body, a log line and a screenshot in a
 * support ticket, and every one of those fields identifies a child.
 */
@Configuration
public class StudentConstraintMappings {

    @Bean
    ConstraintMappingProvider studentConstraintMappingProvider() {
        return () -> List.of(
                mapping("uq_student_admission_number", StudentErrorCode.DUPLICATE_ADMISSION_NUMBER),
                mapping("uq_student_enrolment_one_active", StudentErrorCode.ALREADY_ENROLLED_THIS_SESSION),
                mapping("uq_student_enrolment_roll", StudentErrorCode.ROLL_NUMBER_TAKEN),
                mapping("uq_student_guardian_pair", StudentErrorCode.GUARDIAN_ALREADY_LINKED),
                mapping("uq_student_guardian_one_primary", StudentErrorCode.PRIMARY_GUARDIAN_ALREADY_SET),
                mapping("ck_student_gender", StudentErrorCode.INVALID_STUDENT_GENDER),
                mapping("ck_student_status", StudentErrorCode.INVALID_STUDENT_STATUS),
                mapping("ck_student_guardian_relation", StudentErrorCode.INVALID_GUARDIAN_RELATION));
    }

    /** The common case: the error code's own sentence is the one to show. */
    private static ConstraintMapping mapping(String constraintName, StudentErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
