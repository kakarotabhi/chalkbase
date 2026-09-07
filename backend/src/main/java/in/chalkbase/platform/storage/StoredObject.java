package in.chalkbase.platform.storage;

/**
 * What {@link StorageService#store} actually wrote, handed back so the caller never has to
 * recompute what it just sent.
 *
 * @param relativeKey the same key the caller passed in — returned rather than assumed, so a future
 *     adapter that needs to rewrite it (an extension normalised, a versioned key) can without every
 *     caller changing
 * @param sizeBytes the length actually written
 * @param checksumSha256 lower-case hex SHA-256 of the content, computed by the adapter from the
 *     exact bytes it wrote rather than trusted from the caller — the same reason content type is
 *     sniffed rather than declared (ADR-0025)
 */
public record StoredObject(String relativeKey, long sizeBytes, String checksumSha256) {}
