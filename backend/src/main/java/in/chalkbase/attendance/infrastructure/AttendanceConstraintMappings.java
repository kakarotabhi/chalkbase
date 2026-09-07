package in.chalkbase.attendance.infrastructure;

import in.chalkbase.attendance.domain.AttendanceErrorCode;
import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>{@code uq_attendance_mark_daily} and {@code uq_attendance_mark_period} are two partial unique
 * indexes over the same underlying conflict — "this student already has a mark for that date (and
 * period)" — so both map to the one error code a caller cannot otherwise tell apart.
 * {@code uq_attendance_correction_one_pending} is likewise a partial unique index reported the same
 * way PostgreSQL reports a table constraint.
 */
@Configuration
public class AttendanceConstraintMappings {

    @Bean
    ConstraintMappingProvider attendanceConstraintMappingProvider() {
        return () -> List.of(
                mapping("uq_attendance_mark_daily", AttendanceErrorCode.DUPLICATE_MARK),
                mapping("uq_attendance_mark_period", AttendanceErrorCode.DUPLICATE_MARK),
                mapping("uq_attendance_correction_one_pending", AttendanceErrorCode.CORRECTION_ALREADY_PENDING));
    }

    private static ConstraintMapping mapping(String constraintName, AttendanceErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
