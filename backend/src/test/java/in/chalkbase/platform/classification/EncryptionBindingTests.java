package in.chalkbase.platform.classification;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.platform.crypto.Encrypted;
import in.chalkbase.platform.crypto.EncryptedStringConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * ADR-0022 §2 in force: "every field whose DTO counterpart is {@code RESTRICTED} must carry
 * {@code @Encrypted}." This is what makes that a rule rather than a hope — the same relationship
 * {@code ClassificationTests} enforces for {@code @Classification} itself, one door down.
 *
 * <p><strong>Everything below passes vacuously today.</strong> {@code
 * ClassificationTests.noRestrictedDataHasBeenIntroducedWithoutEncryption} still fails the build if
 * any {@code RESTRICTED} component exists anywhere, so the real, classpath-scanning tests here find
 * nothing to check — which is correct, not a gap. What proves the check itself works is the fixture
 * pair below: a DTO shaped exactly like the one a later lane will add, paired with an entity that
 * gets {@code @Encrypted} wrong in each of the two ways that matter. Delete nothing here when the
 * first Restricted column lands — that is the moment this test starts doing its job.
 */
class EncryptionBindingTests {

    // ── 1. Every Restricted DTO component's counterpart entity field is @Encrypted ──────────────

    @Test
    void everyRestrictedDtoComponentsCounterpartIsEncrypted() {
        List<Class<?>> dtoRecords = scanForDtoRecords();
        List<Class<?>> entities = scanForEntities();

        List<String> offenders = restrictedComponentsMissingEncryption(dtoRecords, entities);

        assertThat(offenders)
                .withFailMessage("""
                        %d RESTRICTED DTO component(s) have no @Encrypted counterpart entity field
                        (ADR-0022):

                        %s

                        A DTO component classified RESTRICTED promises encryption at rest. Add
                        @Encrypted, paired with @Convert(converter = EncryptedStringConverter.class),
                        to the entity field of the same name:

                            @Encrypted @Convert(converter = EncryptedStringConverter.class)
                            @Column(name = "caste_category")
                            private String casteCategory;""", offenders.size(), bullets(offenders))
                .isEmpty();
    }

    // ── 2. Every @Encrypted field is actually wired to the converter ────────────────────────────

    @Test
    void everyEncryptedFieldIsWiredToEncryptedStringConverter() {
        List<String> offenders = encryptedFieldsMissingConverter(scanForEntities());

        assertThat(offenders)
                .withFailMessage("""
                        %d @Encrypted field(s) are not wired to EncryptedStringConverter (ADR-0022):

                        %s

                        @Encrypted alone does not encrypt anything — JPA has no meta-annotation
                        composition for @Convert, so a field is only actually converted if it names
                        the converter itself. Add, verbatim:

                            @Convert(converter = EncryptedStringConverter.class)

                        A field carrying @Encrypted without this is worse than one carrying neither:
                        it reads as protected and is stored in plaintext.""", offenders.size(), bullets(offenders))
                .isEmpty();
    }

    // ── 3. The checks above actually bite ────────────────────────────────────────────────────────

    @Test
    void bitesWhenARestrictedComponentsCounterpartHasNoEncryptedField() {
        List<String> offenders = restrictedComponentsMissingEncryption(
                List.of(FutureRestrictedDto.class), List.of(EntityMissingEncrypted.class));

        assertThat(offenders).containsExactly("FutureRestrictedDto.casteCategory");
    }

    @Test
    void bitesWhenTheEncryptedFieldIsNotWiredToTheConverter() {
        List<String> offenders = restrictedComponentsMissingEncryption(
                List.of(FutureRestrictedDto.class), List.of(EntityWithUnwiredEncrypted.class));

        assertThat(offenders).containsExactly("FutureRestrictedDto.casteCategory");
        assertThat(encryptedFieldsMissingConverter(List.of(EntityWithUnwiredEncrypted.class)))
                .containsExactly("EntityWithUnwiredEncrypted.casteCategory");
    }

    @Test
    void doesNotBiteWhenTheEntityIsProperlyEncrypted() {
        assertThat(restrictedComponentsMissingEncryption(
                        List.of(FutureRestrictedDto.class), List.of(EntityProperlyEncrypted.class)))
                .isEmpty();
        assertThat(encryptedFieldsMissingConverter(List.of(EntityProperlyEncrypted.class)))
                .isEmpty();
    }

    // ── Fixtures: what a later lane's change looks like, correct and wrong ──────────────────────
    //
    // Shaped like the student record ADR-0020 §2 left the Restricted columns out of. None of this
    // is a real DTO or a real entity — introducing an actual RESTRICTED field anywhere in production
    // code is exactly what ClassificationTests.noRestrictedDataHasBeenIntroducedWithoutEncryption
    // still forbids.

    record FutureRestrictedDto(
            @Classification(Tier.INTERNAL) UUID id,
            @Classification(Tier.RESTRICTED) String casteCategory) {
        @Override
        public String toString() {
            return Classified.describe(this);
        }
    }

    /** No @Encrypted at all — the plain omission. */
    static class EntityMissingEncrypted {
        private String casteCategory;
    }

    /** @Encrypted present, but never told which converter — the marker lies. */
    static class EntityWithUnwiredEncrypted {
        @Encrypted private String casteCategory;
    }

    /** The two facts, stated once each, correctly paired. */
    static class EntityProperlyEncrypted {
        @Encrypted @Convert(converter = EncryptedStringConverter.class)
        private String casteCategory;
    }

    // ── The checks themselves ────────────────────────────────────────────────────────────────────

    private static List<String> restrictedComponentsMissingEncryption(
            List<Class<?>> dtoRecords, List<Class<?>> entities) {
        List<String> offenders = new ArrayList<>();
        for (Class<?> dto : dtoRecords) {
            for (RecordComponent component : dto.getRecordComponents()) {
                if (Classified.tierOf(component) != Tier.RESTRICTED) {
                    continue;
                }
                if (!hasEncryptedFieldNamed(entities, component.getName())) {
                    offenders.add(dto.getSimpleName() + "." + component.getName());
                }
            }
        }
        return offenders;
    }

    private static boolean hasEncryptedFieldNamed(List<Class<?>> entities, String fieldName) {
        for (Class<?> entity : entities) {
            for (Field field : entity.getDeclaredFields()) {
                if (field.getName().equals(fieldName) && field.isAnnotationPresent(Encrypted.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> encryptedFieldsMissingConverter(List<Class<?>> entities) {
        List<String> offenders = new ArrayList<>();
        for (Class<?> entity : entities) {
            for (Field field : entity.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Encrypted.class)) {
                    continue;
                }
                Convert convert = field.getAnnotation(Convert.class);
                if (convert == null || !EncryptedStringConverter.class.equals(convert.converter())) {
                    offenders.add(entity.getSimpleName() + "." + field.getName());
                }
            }
        }
        return offenders;
    }

    // ── Scanning ──────────────────────────────────────────────────────────────────────────────

    private static List<Class<?>> scanForDtoRecords() {
        ClassPathScanningCandidateComponentProvider scanner = productionScanner();
        scanner.addIncludeFilter((reader, factory) -> true);

        List<Class<?>> records = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("in.chalkbase")) {
            classOf(candidate).filter(Class::isRecord).ifPresent(records::add);
        }
        assertThat(records)
                .withFailMessage("Found no DTO records under in.chalkbase — the scan is broken, not the code.")
                .isNotEmpty();
        return records;
    }

    private static List<Class<?>> scanForEntities() {
        ClassPathScanningCandidateComponentProvider scanner = productionScanner();
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> entities = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("in.chalkbase")) {
            classOf(candidate).ifPresent(entities::add);
        }
        assertThat(entities)
                .withFailMessage("Found no @Entity classes under in.chalkbase — the scan is broken, not the code.")
                .isNotEmpty();
        return entities;
    }

    /**
     * A scanner with no include filter of its own — every caller adds the one it needs. Spring ORs
     * multiple include filters together, so a catch-all filter added here would make a caller's own
     * {@link AnnotationTypeFilter} pointless.
     */
    private static ClassPathScanningCandidateComponentProvider productionScanner() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        // Production code only. Test fixtures (including the ones in this very file) are not the
        // codebase this test is guarding.
        scanner.addExcludeFilter(
                (reader, factory) -> reader.getResource().getDescription().contains("test-classes"));
        return scanner;
    }

    private static Optional<Class<?>> classOf(BeanDefinition candidate) {
        String name = candidate.getBeanClassName();
        if (name == null) {
            return Optional.empty();
        }
        try {
            // initialize=false: reading annotations must not run anyone's static initialiser.
            return Optional.of(Class.forName(name, false, EncryptionBindingTests.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    private static String bullets(List<String> lines) {
        return lines.stream()
                .map(line -> "  - " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
