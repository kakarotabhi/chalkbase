package in.chalkbase.platform.crypto;

/**
 * A stored value under an {@link Encrypted} column would not decrypt (ADR-0022).
 *
 * <p>Thrown, never returned as a silent {@code null}: a row that will not decrypt means the key is
 * wrong or the data is corrupt, and both need a person to look at it rather than an application that
 * quietly pretends the field was empty.
 *
 * <p><strong>Carries no value that could be sensitive.</strong> The message is a fixed sentence and
 * {@link #keyId()} is a version label such as {@code "v1"} — never the key material, the ciphertext,
 * or anything decrypted or partially decrypted. {@code GlobalExceptionHandler} relies on exactly
 * that: it logs {@link #keyId()} and this exception's fixed message, and deliberately never logs
 * {@link #getCause()} or passes {@code this} to a logger as a {@code Throwable} — doing either would
 * print the wrapped JCE exception's own message and stack, which is one field away from the key.
 */
public final class DecryptionFailedException extends RuntimeException {

    private final String keyId;

    DecryptionFailedException(String keyId, String reason, Throwable cause) {
        super("A stored value could not be decrypted (key id '" + keyId + "'): " + reason, cause);
        this.keyId = keyId;
    }

    /** The key id the ciphertext named, or {@code "<unreadable>"} when the value had no valid one. */
    public String keyId() {
        return keyId;
    }
}
