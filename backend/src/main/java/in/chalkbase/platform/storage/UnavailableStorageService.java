package in.chalkbase.platform.storage;

/**
 * What {@code prod} registers until the S3-compatible adapter ADR-0025 asks the product owner about
 * is approved and configured.
 *
 * <p>Every method throws. This is deliberate and is the whole of what this class is for: the
 * alternatives were refusing the entire application context on {@code prod} — taking down every
 * unrelated feature, on every deployment, until a bucket exists — or quietly falling back to
 * {@link FilesystemStorageService} in a container whose disk does not survive a redeploy, which
 * would accept a document today and lose it on the next deploy without telling anyone. Neither is
 * acceptable for data a parent may need years later, so instead the application boots normally and
 * every document endpoint answers a clear, mapped 503 (ADR-0025).
 */
final class UnavailableStorageService implements StorageService {

    @Override
    public StoredObject store(String relativeKey, byte[] content, String contentType) {
        throw new ObjectStoreUnavailableException();
    }

    @Override
    public byte[] retrieve(String relativeKey) {
        throw new ObjectStoreUnavailableException();
    }

    @Override
    public void delete(String relativeKey) {
        throw new ObjectStoreUnavailableException();
    }
}
