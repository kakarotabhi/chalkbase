package in.chalkbase.platform.storage;

import in.chalkbase.platform.tenancy.TenantContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A real {@link StorageService}, backed by the local filesystem, for {@code local} and {@code test}
 * (ADR-0025).
 *
 * <p>Not a stub: it satisfies every guarantee the port promises, including the tenant boundary —
 * {@link #resolve} prepends the current tenant's schema exactly as the S3-compatible adapter's key
 * construction will — so {@code document}'s tests exercise the real cross-tenant negative case with
 * no bucket, no network and no credentials. This is the same trade
 * {@code EncryptionKeyConfiguration} makes with its fixed development key: a real mechanism, run for
 * real, against data that only ever matters on a developer's machine or a disposable Testcontainer.
 *
 * <p><strong>Never registered on {@code prod}.</strong> A container's local disk does not survive a
 * redeploy, so accepting uploads here in production would mean documents that vanish without a
 * trace — the exact silent failure ADR-0025 chose {@code UnavailableStorageService} over.
 */
final class FilesystemStorageService implements StorageService {

    private final Path root;

    FilesystemStorageService(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the local document storage directory " + root, e);
        }
    }

    @Override
    public StoredObject store(String relativeKey, byte[] content, String contentType) {
        Path target = resolve(relativeKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new StoredObject(relativeKey, content.length, Checksums.sha256(content));
    }

    @Override
    public byte[] retrieve(String relativeKey) {
        Path source = resolve(relativeKey);
        if (!Files.isRegularFile(source)) {
            throw new ObjectNotFoundException();
        }
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String relativeKey) {
        try {
            Files.deleteIfExists(resolve(relativeKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Turns a tenant-relative key into a real path, under the current tenant's own directory.
     *
     * <p>The containment check is not decoration: {@code relativeKey} is always built by the caller
     * from a generated id and a sniffed extension (ADR-0025), never from a user-typed filename, but
     * this is the one place that fact would stop being true without anyone noticing, so it is
     * checked here rather than trusted from every call site.
     */
    private Path resolve(String relativeKey) {
        String schema = TenantContext.currentSchema()
                .orElseThrow(() -> new IllegalStateException(
                        "No tenant bound. A document's storage is always scoped to one school's schema."));
        Path tenantRoot = root.resolve(schema).normalize();
        Path target = tenantRoot.resolve(relativeKey).normalize();
        if (!target.startsWith(tenantRoot)) {
            throw new IllegalArgumentException("relativeKey escapes its tenant directory: " + relativeKey);
        }
        return target;
    }
}
