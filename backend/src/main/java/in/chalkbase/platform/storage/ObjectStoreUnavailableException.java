package in.chalkbase.platform.storage;

/**
 * No working {@link StorageService} adapter is configured on this deployment (ADR-0025).
 *
 * <p>Thrown by {@code UnavailableStorageService}, which is what {@code prod} registers until the
 * S3-compatible adapter is approved and configured. Deliberately a plain {@link RuntimeException}
 * rather than a {@code ChalkbaseException}: this lives in {@code platform.storage}, which knows
 * nothing of any module's {@code ErrorCode}, and {@code platform.error.GlobalExceptionHandler} maps
 * it centrally, the same way it maps {@code DecryptionFailedException}.
 *
 * <p>Carries no detail about which key or which operation — there is nothing actionable in that for
 * a client, and nothing here is a school's data to begin with, but the habit of never building a
 * message from operation-specific detail is the one ADR-0014 asks for everywhere else too.
 */
public class ObjectStoreUnavailableException extends RuntimeException {

    public ObjectStoreUnavailableException() {
        super("No document storage adapter is configured on this deployment (ADR-0025)");
    }
}
