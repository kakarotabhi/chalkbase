package in.chalkbase.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * A short, stable identifier for a set of permissions (ADR-0008).
 *
 * <p><strong>What it is.</strong> A fingerprint of the permission codes a session holds. Two users
 * with the same effective permissions have the same version, at the same school or at different
 * ones; change the set by one code and the version changes. A client keeps the value it was given
 * and, on any {@code 403}, refetches {@code /api/me}: a different version means its view was stale
 * and it re-renders, which turns "the button is there but it fails" into a self-correcting case.
 *
 * <p><strong>What it is not.</strong> It is not a timestamp, not a counter, and not a revision
 * number — it never increases, and a version seen before can come back after a role change is
 * undone. It says nothing about <em>when</em> permissions were resolved, only <em>which</em> set
 * they are. Nothing is stored: there is no database column behind this, deliberately, because a
 * column would have to be bumped by every write that could affect any user's effective set —
 * editing a role, editing a grant, deleting one — and the one that is forgotten is a client that
 * never notices it is stale.
 *
 * <p>Deriving it instead means it is always correct by construction, and costs one SHA-256 of a few
 * hundred bytes on a call that is already doing a database read.
 */
public final class PermissionsVersion {

    /** 8 bytes of SHA-256. A collision needs ~2^32 distinct permission sets; a school has tens. */
    private static final int BYTES = 8;

    private PermissionsVersion() {}

    /**
     * The version of {@code permissions}, independent of their order and of any duplicates.
     *
     * <p>Codes are joined with a newline, which cannot appear inside a permission code, so no two
     * different sets can canonicalise to the same string. An empty set has a version too — the
     * fingerprint of "holds nothing" — rather than a null the client has to special-case.
     */
    public static String of(Collection<String> permissions) {
        String canonical = permissions.stream().distinct().sorted().collect(Collectors.joining("\n"));
        return HexFormat.of().formatHex(sha256(canonical), 0, BYTES);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            // Every JVM ships SHA-256. If this one does not, nothing else here works either.
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
