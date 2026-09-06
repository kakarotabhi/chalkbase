package in.chalkbase.academics.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A session ends after it starts.
 *
 * <p>A type-level constraint rather than {@code @AssertTrue} on a derived getter, and the reason is
 * the error a client is handed. {@code @AssertTrue} attaches its failure to the name of the method
 * it annotates, so a form gets {@code details.endsOnAfterStartsOn} — a name no field on the screen
 * has. {@link EndsAfterStartValidator} reports it against {@code endsOn} instead, which is the box
 * the user has to change and the box the form can highlight.
 *
 * <p>It duplicates {@code ck_academic_session_dates}, deliberately. This one speaks to whoever is
 * filling in the form; the check constraint is what holds when something writes to the table
 * without going through here, and it surfaces through {@code AcademicsConstraintMappings} with no
 * field to attach itself to.
 */
@Documented
@Constraint(validatedBy = EndsAfterStartValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EndsAfterStart {

    String message() default "must be after the date the session starts";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
