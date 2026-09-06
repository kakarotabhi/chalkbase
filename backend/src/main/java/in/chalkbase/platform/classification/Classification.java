package in.chalkbase.platform.classification;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares what tier of ADR-0014 a DTO record component holds.
 *
 * <p>This is the single source of truth the ADR describes. {@link Classified#describe(Object)} reads
 * it to redact a record's {@code toString}, and {@code ClassificationTests} fails the build when a
 * record in an {@code api} package has a component without one. An unannotated component is not
 * assumed safe — it renders as {@code &lt;UNCLASSIFIED&gt;}, never as its value.
 *
 * <p><strong>{@link ElementType#RECORD_COMPONENT} is the only target, deliberately.</strong> With
 * that target alone the annotation is stored in the class file's record component attribute and is
 * visible through {@link java.lang.reflect.RecordComponent#getAnnotation}; it is <em>not</em>
 * propagated to the backing field, the accessor or the constructor parameter. That is what
 * {@code Classified} reads, and restricting the target keeps the classification where ADR-0014 puts
 * it — on the DTO — rather than letting it be sprinkled on arbitrary fields.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Classification {

    /** The tier this component's value belongs to. */
    Tier value();
}
