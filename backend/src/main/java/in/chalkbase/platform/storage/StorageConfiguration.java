package in.chalkbase.platform.storage;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Wires a {@link StorageService} to whichever adapter its profile gets (ADR-0025, amended).
 *
 * <p>The profile split here is the opposite shape from {@code EncryptionKeyConfiguration}'s, and
 * deliberately: encryption is wired the same way on every profile because a Restricted column can
 * be written from the first line of code on a developer's own machine, so {@code local} and
 * {@code test} need a real key too, just not a secret one. Storage has no such column to protect
 * yet — nothing is written until a document is uploaded, and {@code local}/{@code test} get their
 * own real, non-network adapter for exactly that reason — so {@code prod} is free to get a
 * genuinely different adapter rather than a fallback with the same shape as the real one.
 *
 * <p><strong>{@code prod} without configuration still boots and still answers 503.</strong> Unlike
 * {@code EncryptionKeyConfiguration}, which refuses to start {@code prod} at all on a missing key,
 * {@link #s3StorageService} falls back to {@link UnavailableStorageService} when any one of the
 * five environment values is absent, rather than failing application startup. ADR-0025 gives the
 * reason: a Restricted column can be written the moment any code path reaches it, so encryption
 * cannot be allowed to come up half-configured, but nothing uploads a document until this module's
 * own endpoints are called, so the choice is between "every other feature also refuses to boot"
 * and "every document endpoint answers a clear, mapped 503" — and the ADR already chose the second
 * for exactly this deployment, before the S3-compatible adapter existed to be misconfigured.
 */
@Configuration(proxyBeanMethods = false)
class StorageConfiguration {

    /** {@code local} and {@code test} both get a real filesystem adapter — see its own Javadoc. */
    @Profile("!prod")
    @Bean
    StorageService filesystemStorageService(
            @Value("${chalkbase.storage.local-path:${java.io.tmpdir}/chalkbase-storage}") String localPath) {
        return new FilesystemStorageService(Path.of(localPath));
    }

    /**
     * {@code prod} gets the S3-compatible adapter once every one of these five values is set, and a
     * clean, loud {@link UnavailableStorageService} otherwise — see the class Javadoc for why a
     * missing value falls back rather than failing startup.
     *
     * <p>Every value is named in {@code docs/operations/document-storage.md}, with where to find it
     * in the Supabase dashboard. None of the five is ever logged: a malformed {@code endpoint} is
     * the one value whose own text can appear in a startup failure message, via {@code URI.create}
     * rejecting it, and it is a URL rather than a secret; the other four never reach an exception
     * message at all.
     */
    @Profile("prod")
    @Bean
    StorageService s3StorageService(
            @Value("${chalkbase.storage.s3.endpoint:}") String endpoint,
            @Value("${chalkbase.storage.s3.region:}") String region,
            @Value("${chalkbase.storage.s3.bucket:}") String bucket,
            @Value("${chalkbase.storage.s3.access-key:}") String accessKey,
            @Value("${chalkbase.storage.s3.secret-key:}") String secretKey) {
        if (!StringUtils.hasText(endpoint)
                || !StringUtils.hasText(region)
                || !StringUtils.hasText(bucket)
                || !StringUtils.hasText(accessKey)
                || !StringUtils.hasText(secretKey)) {
            return new UnavailableStorageService();
        }

        URI endpointUri;
        try {
            endpointUri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "CHALKBASE_STORAGE_ENDPOINT is not a valid URL: \"" + endpoint
                            + "\". Copy the S3-compatible endpoint from the Supabase Storage settings.",
                    e);
        }

        S3Client client = S3Client.builder()
                .endpointOverride(endpointUri)
                // Set explicitly rather than left to the default provider chain, which would
                // otherwise try environment variables the SDK does not know this application uses,
                // a shared credentials file that does not exist in this container, and finally the
                // EC2/ECS instance-metadata service — a network call this deployment is not running
                // on EC2 or ECS to answer, and one this ADR's startup-time budget has no room for.
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                // Likewise explicit: a bare `Region.of(region)` still consults instance metadata if
                // asked to *guess* a region, but naming one here never triggers that lookup.
                .region(Region.of(region))
                // Supabase Storage's S3 endpoint does not serve virtual-host-style bucket URLs
                // (`<bucket>.<endpoint>`) the way AWS's own S3 does — only `<endpoint>/<bucket>/...`
                // — and most self-hosted S3-compatible targets share that limitation. Getting this
                // wrong produces a DNS failure for a host that was never going to exist, which reads
                // as a network problem rather than as what it is.
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // The dependency decision (ADR-0025, amended): a thin wrapper over the JDK's own
                // `HttpURLConnection`, not the async Netty client or the full Apache HttpClient 5
                // stack the `s3` module would otherwise bring in — see the exclusions in `pom.xml`.
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();

        return new S3StorageService(client, bucket);
    }
}
