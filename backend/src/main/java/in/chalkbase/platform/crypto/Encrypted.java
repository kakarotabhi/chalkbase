package in.chalkbase.platform.crypto;

import jakarta.persistence.Convert;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity field as stored encrypted at rest ({@link EncryptedStringConverter}, ADR-0022).
 *
 * <p>This is the storage half of a pair that is deliberately never merged into one annotation.
 * {@link in.chalkbase.platform.classification.Classification Classification} stays on the DTO and
 * describes <em>disclosure</em> — what may be logged, shown or exported. {@code @Encrypted} lives on
 * the entity and describes <em>storage</em>. {@code EncryptionBindingTests} (beside
 * {@code ClassificationTests}) is the build-failing test that keeps the two facts from disagreeing:
 * every field whose DTO counterpart is {@code RESTRICTED} must carry this annotation.
 *
 * <h2>This annotation does not, by itself, encrypt anything</h2>
 *
 * <p>Deliberately: JPA has no meta-annotation composition for {@link Convert}, so a field is only
 * ever actually converted if it names the converter itself. Pair every {@code @Encrypted} field with
 * the converter, explicitly, in the same breath:
 *
 * <pre>{@code
 * @Encrypted
 * @Convert(converter = EncryptedStringConverter.class)
 * @Column(name = "caste_category")
 * private String casteCategory;
 * }</pre>
 *
 * <p>{@code EncryptionBindingTests} also fails the build for an {@code @Encrypted} field missing that
 * {@code @Convert} — a marker that does not wire up the converter is a lie, and lying about whether a
 * child's caste is actually encrypted is exactly the failure mode this exists to remove.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Encrypted {}
