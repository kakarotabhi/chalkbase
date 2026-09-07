package in.chalkbase.platform.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * {@link EncryptedStringConverter} in isolation, with no Spring context — the thing under test is
 * AES-GCM, the {@code <keyId>:<base64>} shape, and the multi-key read path, none of which need one.
 *
 * <p>Every assertion here that checks a failure's message is also checking ADR-0022's promise that
 * the key, the ciphertext and the plaintext never appear in one: a poisoned-looking sentinel is
 * planted in the plaintext specifically so "this must not be echoed back" has something to look for.
 */
class EncryptedStringConverterTests {

    private static final String PLAINTEXT = "General Category — Zx7SentinelZx";

    private final SecretKey keyA = generateKey();
    private final SecretKey keyB = generateKey();

    private final EncryptedStringConverter converter =
            new EncryptedStringConverter(new EncryptionKeys("v1", Map.of("v1", keyA)));

    @Test
    void nullPassesThroughInBothDirections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTripsThroughTheCurrentKey() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);

        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(PLAINTEXT);
    }

    @Test
    void everyCiphertextIsPrefixedWithTheCurrentKeyId() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);

        assertThat(stored).startsWith("v1:");
        assertThatCode(() -> Base64.getDecoder().decode(stored.substring("v1:".length())))
                .doesNotThrowAnyException();
    }

    @Test
    void theSamePlaintextEncryptsDifferentlyEveryTime() {
        String first = converter.convertToDatabaseColumn(PLAINTEXT);
        String second = converter.convertToDatabaseColumn(PLAINTEXT);

        assertThat(first)
                .as("a fresh random nonce per write is what makes AES-GCM safe to reuse a key under")
                .isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(PLAINTEXT);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(PLAINTEXT);
    }

    @Test
    void readsAcceptAnOlderKeyWhileWritesAlwaysUseTheCurrentOne() {
        // v2 wrote this row before a rotation moved "current" to v1 — the shape a real rotation
        // takes: the old key is kept around for reads, never used for a new write.
        EncryptedStringConverter writerUnderV2 =
                new EncryptedStringConverter(new EncryptionKeys("v2", Map.of("v2", keyB)));
        String writtenUnderV2 = writerUnderV2.convertToDatabaseColumn(PLAINTEXT);
        assertThat(writtenUnderV2).startsWith("v2:");

        EncryptedStringConverter afterRotation =
                new EncryptedStringConverter(new EncryptionKeys("v1", Map.of("v1", keyA, "v2", keyB)));

        assertThat(afterRotation.convertToEntityAttribute(writtenUnderV2)).isEqualTo(PLAINTEXT);
        assertThat(afterRotation.convertToDatabaseColumn(PLAINTEXT))
                .as("a new write always uses the current key, never an old one still kept for reads")
                .startsWith("v1:");
    }

    @Test
    void anUnknownKeyIdIsAnErrorNeverASilentNull() {
        String storedUnderAKeyWeNeverHad = "v9:" + Base64.getEncoder().encodeToString(new byte[24]);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(storedUnderAKeyWeNeverHad))
                .isInstanceOf(DecryptionFailedException.class)
                .extracting(ex -> ((DecryptionFailedException) ex).keyId())
                .isEqualTo("v9");
    }

    @Test
    void aTamperedOrCorruptCiphertextIsAnErrorNeverASilentNullOrTheWrongPlaintext() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(DecryptionFailedException.class)
                .extracting(ex -> ((DecryptionFailedException) ex).keyId())
                .isEqualTo("v1");
    }

    @Test
    void aMissingKeyIdPrefixIsAnError() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-prefixed-value"))
                .isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void decryptedWithTheWrongKeyIsAnErrorNeverTheWrongPlaintext() {
        String storedUnderKeyA = converter.convertToDatabaseColumn(PLAINTEXT);
        // Same key id, different key — the shape of "the deployment has the wrong key configured".
        EncryptedStringConverter wrongKey = new EncryptedStringConverter(new EncryptionKeys("v1", Map.of("v1", keyB)));

        assertThatThrownBy(() -> wrongKey.convertToEntityAttribute(storedUnderKeyA))
                .isInstanceOf(DecryptionFailedException.class);
    }

    @Test
    void aDecryptionFailuresMessageNeverContainsTheKeyTheCiphertextOrThePlaintext() {
        String stored = converter.convertToDatabaseColumn(PLAINTEXT);
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        String message = catchDecryptionFailure(() -> converter.convertToEntityAttribute(tampered))
                .getMessage();

        assertThat(message)
                .doesNotContain(PLAINTEXT)
                .doesNotContain("Zx7SentinelZx")
                .doesNotContain(Base64.getEncoder().encodeToString(keyA.getEncoded()))
                .doesNotContain(tampered.substring(tampered.indexOf(':') + 1));
    }

    private static DecryptionFailedException catchDecryptionFailure(Runnable action) {
        try {
            action.run();
        } catch (DecryptionFailedException e) {
            return e;
        }
        throw new AssertionError("expected a DecryptionFailedException");
    }

    private static SecretKey generateKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return new SecretKeySpec(bytes, "AES");
    }
}
