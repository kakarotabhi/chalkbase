package in.chalkbase.platform.crypto;

import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * The keys {@link EncryptedStringConverter} knows about, by key id (ADR-0022).
 *
 * <p>Exactly one key is configured today, {@value ID_V1}, from {@code CHALKBASE_ENCRYPTION_KEY}
 * ({@code EncryptionKeyConfiguration}). This type exists anyway, ahead of a second key actually
 * existing, because it is the shape rotation takes: a new key arrives as a second entry in
 * {@code keysById}, {@code currentKeyId} moves to point at it, and every ciphertext already written
 * keeps decrypting under the id it names. Without a map here, "two keys can be live at once" would
 * mean rewriting the converter at rotation time instead of adding one line to it.
 */
final class EncryptionKeys {

    /** The only key id that exists yet. Not a format restriction — the next one is free to be "v2". */
    static final String ID_V1 = "v1";

    private final String currentKeyId;
    private final Map<String, SecretKey> keysById;

    EncryptionKeys(String currentKeyId, Map<String, SecretKey> keysById) {
        if (!keysById.containsKey(currentKeyId)) {
            throw new IllegalArgumentException(
                    "currentKeyId '" + currentKeyId + "' is not among the configured key ids " + keysById.keySet());
        }
        this.currentKeyId = currentKeyId;
        this.keysById = Map.copyOf(keysById);
    }

    /** The id every new write is encrypted under. */
    String currentKeyId() {
        return currentKeyId;
    }

    /** The key every new write is encrypted under. */
    SecretKey currentKey() {
        return keysById.get(currentKeyId);
    }

    /** The key a ciphertext prefixed with {@code keyId} should be read with, if we still have it. */
    Optional<SecretKey> keyFor(String keyId) {
        return Optional.ofNullable(keysById.get(keyId));
    }
}
