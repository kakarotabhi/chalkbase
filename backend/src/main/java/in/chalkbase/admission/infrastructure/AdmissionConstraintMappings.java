package in.chalkbase.admission.infrastructure;

import in.chalkbase.platform.error.ConstraintMapping;
import in.chalkbase.platform.error.ConstraintMappingProvider;
import in.chalkbase.platform.error.PlatformErrorCode;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What this module's database constraints mean to a user.
 *
 * <p>Neither foreign key below is expected to fire in ordinary use — {@code AdmissionEnquiryService}
 * validates the class and the counsellor before every write that names one — but a race between two
 * requests (a class retired, a counsellor's account deleted, between the form loading and the save)
 * can still reach the database first. Both map to the same generic conflict a caller already knows
 * how to show, rather than a raw constraint name.
 */
@Configuration
public class AdmissionConstraintMappings {

    @Bean
    ConstraintMappingProvider admissionConstraintMappingProvider() {
        return () -> List.of(mapping("fk_enquiry_class"), mapping("fk_enquiry_counsellor"));
    }

    private static ConstraintMapping mapping(String constraintName) {
        return new ConstraintMapping(
                constraintName, PlatformErrorCode.CONFLICT, PlatformErrorCode.CONFLICT.defaultMessage());
    }
}
