package in.chalkbase.school.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An id {@link java.time.ZoneId} recognises, e.g. {@code Asia/Kolkata}.
 *
 * <p>Not a pattern. A regexp can rule out characters that cannot appear in a zone id, but it cannot
 * tell {@code Asia/Kolkata} from {@code Asia/Kalkota} — both look like a zone, only one is one, and
 * a typo that gets past a pattern silently renders every timestamp this school reads wrong.
 * {@link ValidTimeZoneValidator} asks the platform's own IANA database instead of guessing at its
 * shape.
 *
 * <p>{@code @NotBlank} owns the empty case; this says nothing about it, the same convention
 * {@code EndsAfterStart} in {@code academics.api} follows for its own field.
 */
@Documented
@Constraint(validatedBy = ValidTimeZoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {

    String message() default "must be a time zone Chalkbase knows, for example Asia/Kolkata";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
