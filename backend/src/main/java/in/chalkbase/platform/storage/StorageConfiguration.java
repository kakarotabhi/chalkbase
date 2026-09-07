package in.chalkbase.platform.storage;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires a {@link StorageService} to whichever adapter its profile gets (ADR-0025).
 *
 * <p>The profile split here is the opposite shape from {@code EncryptionKeyConfiguration}'s, and
 * deliberately: encryption is wired the same way on every profile because a Restricted column can
 * be written from the first line of code on a developer's own machine, so {@code local} and
 * {@code test} need a real key too, just not a secret one. Storage has no such column to protect
 * yet — nothing is written until a document is uploaded, and nothing uploads on {@code prod} today
 * — so {@code prod} is free to get a genuinely different adapter rather than a fallback with the
 * same shape as the real one.
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

    /** {@code prod} gets a clean, loud "not available yet" until the S3-compatible adapter lands. */
    @Profile("prod")
    @Bean
    StorageService unavailableStorageService() {
        return new UnavailableStorageService();
    }
}
