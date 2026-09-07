package in.chalkbase.platform.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The startup half of the encryption key (ADR-0022): what happens before a single request is
 * served, mirroring {@code SetupKeyConfigurationTests} for the same reason that one is not a
 * {@code @SpringBootTest} — a failure here must have exactly one possible cause.
 *
 * <p>Unlike {@code SetupKeyConfiguration}, this configuration is <strong>not</strong> {@code
 * @Profile("prod")}: every profile needs a working {@link EncryptionKeys} bean from the moment an
 * {@code @Encrypted} field can be read or written, so the profile check happens inside the bean
 * method instead of on the class.
 */
class EncryptionKeyConfigurationTests {

    private static final String VALID_KEY = "qiL6s/vMQHcnBUXU2fjU/oBr8lQDOCi7CNL/N+gSP3s=";

    private final ApplicationContextRunner contexts =
            new ApplicationContextRunner().withUserConfiguration(EncryptionKeyConfiguration.class);

    @Test
    void refusesToStartOnProdWithNoKey() {
        contexts.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CHALKBASE_ENCRYPTION_KEY");
        });
    }

    @Test
    void refusesToStartOnProdWithABlankKey() {
        contexts.withPropertyValues("spring.profiles.active=prod", "chalkbase.encryption.key=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("CHALKBASE_ENCRYPTION_KEY");
                });
    }

    @Test
    void refusesToStartOnProdWithAKeyThatIsNotValidBase64() {
        contexts.withPropertyValues("spring.profiles.active=prod", "chalkbase.encryption.key=not base64 at all!")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("base64");
                });
    }

    @Test
    void refusesToStartOnProdWithAKeyOfTheWrongLength() {
        // Valid base64, but 16 bytes (AES-128) rather than the 32 ADR-0022 asks for.
        contexts.withPropertyValues("spring.profiles.active=prod", "chalkbase.encryption.key=MTIzNDU2Nzg5MDEyMzQ1Ng==")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("32 bytes");
                });
    }

    @Test
    void startsOnProdWhenTheKeyIsSetAndValid() {
        contexts.withPropertyValues("spring.profiles.active=prod", "chalkbase.encryption.key=" + VALID_KEY)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EncryptedStringConverter.class));
    }

    /**
     * The condition that keeps local development and the test suite unchanged: a developer never
     * has to mint a key before the application, or a test touching an {@code @Encrypted} field,
     * will run.
     */
    @Test
    void fallsBackToTheCheckedInDevelopmentKeyOffTheProdProfile() {
        contexts.run(context -> assertThat(context).hasNotFailed().hasSingleBean(EncryptedStringConverter.class));

        contexts.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EncryptedStringConverter.class));

        contexts.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EncryptedStringConverter.class));
    }

    @Test
    void anExplicitlyConfiguredKeyIsStillValidatedOffTheProdProfile() {
        contexts.withPropertyValues("spring.profiles.active=local", "chalkbase.encryption.key=too-short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().hasMessageContaining("base64");
                });
    }
}
