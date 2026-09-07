package in.chalkbase.platform.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-GCM {@link AttributeConverter} for a column marked {@link Encrypted} (ADR-0022).
 *
 * <p>Stored shape: {@code <keyId>:<base64 of nonce || ciphertext-and-tag>} — for example
 * {@code v1:R2VuZXJhdGVkTm9uY2VBbmRDaXBoZXJ0ZXh0...}. The key id is what lets two keys be live at
 * once: every write uses {@link EncryptionKeys#currentKey()}, and a read looks the prefix up in
 * {@link EncryptionKeys#keyFor(String)} rather than assuming the current key wrote every row. A
 * fresh, unpredictable nonce is drawn for every write, never reused — GCM's authentication guarantee
 * depends on that, not merely its confidentiality.
 *
 * <p><strong>Not {@code autoApply}.</strong> Every {@code String} column would otherwise be run
 * through this, encrypting data nobody classified as Restricted and silently corrupting every column
 * this converter was never meant to touch. A field opts in explicitly:
 * {@code @Encrypted @Convert(converter = EncryptedStringConverter.class)} — see {@link Encrypted}'s
 * Javadoc for why both are required.
 *
 * <p>Registered as a Spring bean ({@code EncryptionKeyConfiguration}) rather than left for Hibernate
 * to construct with a no-arg constructor: Spring Boot's Hibernate integration resolves a
 * {@code @Converter} through the application context first, which is what lets this take the key
 * material as a constructor argument instead of reaching for a static singleton.
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;
    private static final String KEY_ID_SEPARATOR = ":";

    private final EncryptionKeys keys;
    private final SecureRandom random = new SecureRandom();

    EncryptedStringConverter(EncryptionKeys keys) {
        this.keys = keys;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        random.nextBytes(nonce);

        byte[] cipherTextAndTag;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.currentKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            cipherTextAndTag = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            // The key is decoded and length-checked at startup (EncryptionKeyConfiguration), so
            // reaching here means the JVM's crypto provider itself is broken — not something a caller
            // did. Still routed through the same safe exception rather than left to leak whatever
            // GeneralSecurityException.getMessage() happens to say.
            throw new DecryptionFailedException(keys.currentKeyId(), "could not encrypt under the current key", e);
        }

        byte[] nonceAndCipherText = new byte[nonce.length + cipherTextAndTag.length];
        System.arraycopy(nonce, 0, nonceAndCipherText, 0, nonce.length);
        System.arraycopy(cipherTextAndTag, 0, nonceAndCipherText, nonce.length, cipherTextAndTag.length);

        return keys.currentKeyId() + KEY_ID_SEPARATOR + Base64.getEncoder().encodeToString(nonceAndCipherText);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }

        int separator = stored.indexOf(KEY_ID_SEPARATOR);
        if (separator < 0) {
            throw new DecryptionFailedException("<unreadable>", "the stored value has no key id prefix", null);
        }
        String keyId = stored.substring(0, separator);
        SecretKey key = keys.keyFor(keyId)
                .orElseThrow(() -> new DecryptionFailedException(keyId, "no key with that id is configured", null));

        byte[] nonceAndCipherText;
        try {
            nonceAndCipherText = Base64.getDecoder().decode(stored.substring(separator + 1));
        } catch (IllegalArgumentException e) {
            throw new DecryptionFailedException(keyId, "the stored value is not valid base64", e);
        }
        if (nonceAndCipherText.length <= GCM_NONCE_LENGTH_BYTES) {
            throw new DecryptionFailedException(keyId, "the stored value is shorter than one nonce", null);
        }
        byte[] nonce = Arrays.copyOfRange(nonceAndCipherText, 0, GCM_NONCE_LENGTH_BYTES);
        byte[] cipherTextAndTag =
                Arrays.copyOfRange(nonceAndCipherText, GCM_NONCE_LENGTH_BYTES, nonceAndCipherText.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] plainText = cipher.doFinal(cipherTextAndTag);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException (wrong key or tampered/corrupt bytes) lands here, same as any other
            // decrypt failure. GCM authenticates, so this is the only outcome of a mismatch — never a
            // wrong-but-plausible plaintext.
            throw new DecryptionFailedException(keyId, "the key did not match or the data is corrupt", e);
        }
    }
}
