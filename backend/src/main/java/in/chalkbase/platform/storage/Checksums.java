package in.chalkbase.platform.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256, computed from the exact bytes an adapter is about to write or just read — never trusted
 * from a caller (ADR-0025). Shared by every {@link StorageService} adapter so the algorithm and its
 * hex encoding are stated once rather than copied per adapter.
 */
final class Checksums {

    private Checksums() {}

    static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // Every JDK ships SHA-256; this is not a real branch.
            throw new IllegalStateException(e);
        }
    }
}
