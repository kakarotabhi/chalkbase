package in.chalkbase.document.infrastructure;

import in.chalkbase.document.domain.DocumentErrorCode;
import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>{@code ck_document_type} and {@code ck_document_verification_status} are unreachable through
 * the API today — the request DTOs already bind to the enum, so an unknown value fails as a
 * {@code MethodArgumentTypeMismatchException} before this module sees it — and are claimed anyway,
 * the same way {@code AcademicsConstraintMappings} claims constraints its own DTOs already validate:
 * this is what the violation is called when something writes without going through the API.
 */
@Configuration
public class DocumentConstraintMappings {

    @Bean
    ConstraintMappingProvider documentConstraintMappingProvider() {
        return () -> List.of(
                mapping("fk_document_student", DocumentErrorCode.UNKNOWN_STUDENT),
                mapping("ck_document_dates", DocumentErrorCode.EXPIRY_BEFORE_ISSUE),
                mapping("ck_document_type", DocumentErrorCode.INVALID_DOCUMENT_TYPE),
                mapping("ck_document_verification_status", DocumentErrorCode.INVALID_VERIFICATION_STATUS));
    }

    private static ConstraintMapping mapping(String constraintName, DocumentErrorCode errorCode) {
        return new ConstraintMapping(constraintName, errorCode, errorCode.defaultMessage());
    }
}
