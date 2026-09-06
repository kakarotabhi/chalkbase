package in.chalkbase.academics.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Reports the failure against {@code endsOn} rather than against the object.
 *
 * <p>Says nothing when either date is missing: {@code @NotNull} already owns that failure, and two
 * messages about one empty box is one more than a form can show.
 */
class EndsAfterStartValidator implements ConstraintValidator<EndsAfterStart, SaveAcademicSessionRequest> {

    @Override
    public boolean isValid(SaveAcademicSessionRequest request, ConstraintValidatorContext context) {
        if (request == null || request.startsOn() == null || request.endsOn() == null) {
            return true;
        }
        if (request.endsOn().isAfter(request.startsOn())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endsOn")
                .addConstraintViolation();
        return false;
    }
}
