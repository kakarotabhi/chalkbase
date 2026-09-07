package in.chalkbase.platform.crypto;

import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Wires {@link EncryptedStringConverter} to its key — on every profile, unlike
 * {@code SetupKeyConfiguration}, because a Restricted column can be read or written from the moment
 * the first developer starts the application, not only in production.
 *
 * <p><strong>{@code prod} refuses to start on a missing or malformed key</strong>, for the same
 * reason {@code SetupKeyConfiguration} refuses to start without {@code CHALKBASE_SETUP_KEY} — and
 * ADR-0022 says the reasoning applies harder here: a deployment that silently fell back to storing a
 * child's caste or religion in plaintext would look healthy on every health check and be a DPDP
 * violation from the first row written. Refusing to boot is loud and happens before the port binds.
 *
 * <p><strong>{@code local} and {@code test} get a fixed, checked-in key</strong> ({@link
 * #DEVELOPMENT_KEY_BASE64}) when {@code CHALKBASE_ENCRYPTION_KEY} is not set, which it normally is
 * not on either profile. The alternative — requiring every developer to mint and export a key before
 * {@code ./mvnw spring-boot:run} works, or before any test touching an {@code @Encrypted} field can
 * run — is friction for no safety: this key is public, this repository is public, and the only data
 * it ever protects is {@code local}'s invented demo school ({@code DemoSchoolSeeder}) or {@code
 * test}'s disposable Testcontainer. Encryption still runs for real on both profiles — the nonce is
 * still random per write and a wrong key still fails to decrypt — so a bug in the converter itself is
 * caught the same way it would be in production. What is not defensible is silently turning
 * encryption <em>off</em> on the {@code prod} profile, which is why there is no such branch: every
 * profile encrypts, they simply do not all have to be told the same key.
 */
@Configuration(proxyBeanMethods = false)
class EncryptionKeyConfiguration {

    /**
     * A real, generated 256-bit key, committed to a public repository on purpose. It has no other
     * use and must never protect real data — see the class Javadoc. Generated with {@code openssl
     * rand -base64 32}, the same command the operational note asks whoever sets
     * {@code CHALKBASE_ENCRYPTION_KEY} in a real deployment to run for their own, secret, key.
     */
    static final String DEVELOPMENT_KEY_BASE64 = "qiL6s/vMQHcnBUXU2fjU/oBr8lQDOCi7CNL/N+gSP3s=";

    @Bean
    EncryptionKeys encryptionKeys(
            @Value("${chalkbase.encryption.key:}") String configuredKey, Environment environment) {
        String currentKeyId = EncryptionKeys.ID_V1;
        boolean prod = environment.matchesProfiles("prod");

        if (!StringUtils.hasText(configuredKey)) {
            if (prod) {
                // The message names the variable and not the value, and there is no value to name yet.
                throw new IllegalStateException(
                        "CHALKBASE_ENCRYPTION_KEY must be set on the prod profile. Every Restricted column"
                                + " (ADR-0022) is encrypted under it, and there is no recovery from losing it —"
                                + " back it up somewhere that is not this machine. Generate one with"
                                + " `openssl rand -base64 32` and set it in the deployment's environment.");
            }
            configuredKey = DEVELOPMENT_KEY_BASE64;
        }

        SecretKey key = decode(configuredKey);
        return new EncryptionKeys(currentKeyId, Map.of(currentKeyId, key));
    }

    @Bean
    EncryptedStringConverter encryptedStringConverter(EncryptionKeys keys) {
        return new EncryptedStringConverter(keys);
    }

    /**
     * Decodes and length-checks a key eagerly, at startup, rather than leaving a malformed
     * {@code CHALKBASE_ENCRYPTION_KEY} to surface as a confusing crypto exception on the first write
     * any school makes.
     */
    private static SecretKey decode(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "CHALKBASE_ENCRYPTION_KEY is not valid base64. Generate one with `openssl rand -base64 32`.", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("CHALKBASE_ENCRYPTION_KEY must decode to 32 bytes (256 bits) for"
                    + " AES-256; this one decoded to " + bytes.length + ". Generate one with"
                    + " `openssl rand -base64 32`.");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
