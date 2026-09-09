package in.chalkbase.fee.infrastructure;

import in.chalkbase.fee.domain.FeeErrorCode;
import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>{@code uq_fee_structure_one_current} and {@code uq_fee_structure_version} should be
 * unreachable through the API — {@code FeeStructureService#save} always writes the next version
 * number and supersedes the previous one in the same transaction — and are mapped anyway, for the
 * same reason {@code AcademicsConstraintMappings} maps {@code uq_academic_session_one_current}:
 * something that writes outside this service should get a sentence, not a generic conflict.
 */
@Configuration
public class FeeConstraintMappings {

    @Bean
    ConstraintMappingProvider feeConstraintMappingProvider() {
        return () -> List.of(
                mapping("uq_fee_head_name", FeeErrorCode.DUPLICATE_FEE_HEAD_NAME),
                mapping("uq_fee_concession_type_name", FeeErrorCode.DUPLICATE_CONCESSION_TYPE_NAME),
                mapping("uq_fee_structure_one_current", FeeErrorCode.STRUCTURE_SESSION_CLOSED),
                mapping("uq_fee_structure_version", FeeErrorCode.STRUCTURE_SESSION_CLOSED),
                mapping("uq_fee_structure_item_head", FeeErrorCode.DUPLICATE_HEAD_IN_STRUCTURE),
                mapping("uq_fee_installment_due_date", FeeErrorCode.DUPLICATE_INSTALLMENT_DATE));
    }

    private static ConstraintMapping mapping(String constraintName, FeeErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
