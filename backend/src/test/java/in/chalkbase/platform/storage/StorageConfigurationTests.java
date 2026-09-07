package in.chalkbase.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The startup half of the S3-compatible adapter (ADR-0025, amended): which {@link StorageService}
 * a profile gets, and — the one case this configuration handles differently from every other
 * environment-backed bean in {@code platform} — that {@code prod} boots and answers 503 rather than
 * refusing to start when the five S3 values are absent. Mirrors {@code
 * EncryptionKeyConfigurationTests} in shape; see this configuration's own Javadoc for why the two
 * behave oppositely on a missing value.
 */
class StorageConfigurationTests {

    private static final String ENDPOINT = "chalkbase.storage.s3.endpoint=https://project.supabase.co/storage/v1/s3";
    private static final String REGION = "chalkbase.storage.s3.region=ap-south-1";
    private static final String BUCKET = "chalkbase.storage.s3.bucket=chalkbase-documents";
    private static final String ACCESS_KEY = "chalkbase.storage.s3.access-key=AKIAEXAMPLE";
    private static final String SECRET_KEY = "chalkbase.storage.s3.secret-key=example-secret";

    private final ApplicationContextRunner contexts =
            new ApplicationContextRunner().withUserConfiguration(StorageConfiguration.class);

    @Test
    void localAndTestGetTheFilesystemAdapterRegardlessOfAnyS3Configuration() {
        contexts.run(context -> assertThat(context)
                .hasNotFailed()
                .getBean(StorageService.class)
                .isInstanceOf(FilesystemStorageService.class));

        contexts.withPropertyValues("spring.profiles.active=local", ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(StorageService.class)
                        .isInstanceOf(FilesystemStorageService.class));

        contexts.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(StorageService.class)
                        .isInstanceOf(FilesystemStorageService.class));
    }

    /**
     * The behaviour ADR-0025 asks for by name: a {@code prod} deployment with nothing configured
     * still boots, and every document endpoint answers a mapped 503 rather than the whole
     * application refusing to start.
     */
    @Test
    void prodWithNoS3ConfigurationBootsWithTheUnavailableAdapter() {
        contexts.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(StorageService.class)
                        .isInstanceOf(UnavailableStorageService.class));
    }

    /** Any one of the five missing is the same as none of them — there is no partial credit. */
    @Test
    void prodWithOneOfTheFiveValuesMissingStillGetsTheUnavailableAdapter() {
        contexts.withPropertyValues("spring.profiles.active=prod", REGION, BUCKET, ACCESS_KEY, SECRET_KEY)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(StorageService.class)
                        .isInstanceOf(UnavailableStorageService.class));
    }

    @Test
    void prodWithAllFiveValuesGetsTheS3Adapter() {
        contexts.withPropertyValues("spring.profiles.active=prod", ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(StorageService.class)
                        .isInstanceOf(S3StorageService.class));
    }

    @Test
    void prodWithAMalformedEndpointFailsToStartRatherThanSilentlyFallingBack() {
        contexts.withPropertyValues(
                        "spring.profiles.active=prod",
                        "chalkbase.storage.s3.endpoint=not a url",
                        REGION,
                        BUCKET,
                        ACCESS_KEY,
                        SECRET_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context)
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("CHALKBASE_STORAGE_ENDPOINT");
                });
    }
}
