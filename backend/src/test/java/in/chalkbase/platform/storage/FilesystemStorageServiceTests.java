package in.chalkbase.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.chalkbase.platform.tenancy.TenantContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one class ADR-0025 names as the highest-value thing to review carefully: this is where the
 * tenant boundary for a school's files is actually enforced, since the object store itself has no
 * structural notion of a schema the way PostgreSQL does. No Spring context — the claims under test
 * are about {@link TenantContext} and the filesystem, not about wiring.
 */
class FilesystemStorageServiceTests {

    private static final String SCHOOL_A = "riverbank_documents";
    private static final String SCHOOL_B = "cloverdale_documents";

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void writesAndReadsBackTheSameBytes(@TempDir Path root) {
        StorageService storage = new FilesystemStorageService(root);
        TenantContext.set(SCHOOL_A);

        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        StoredObject stored = storage.store("documents/one.pdf", content, "application/pdf");

        assertThat(stored.relativeKey()).isEqualTo("documents/one.pdf");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(storage.retrieve("documents/one.pdf")).isEqualTo(content);
    }

    /**
     * The claim ADR-0025's tenancy section rests its whole argument on: the same relative key,
     * written by two different tenants, resolves to two different objects — one school's upload
     * never becomes visible to another's read of the identical key.
     */
    @Test
    void twoTenantsWritingTheSameRelativeKeyDoNotCollide(@TempDir Path root) {
        StorageService storage = new FilesystemStorageService(root);

        TenantContext.set(SCHOOL_A);
        storage.store(
                "documents/same-key.pdf", "school A's document".getBytes(StandardCharsets.UTF_8), "application/pdf");

        TenantContext.set(SCHOOL_B);
        storage.store(
                "documents/same-key.pdf", "school B's document".getBytes(StandardCharsets.UTF_8), "application/pdf");

        TenantContext.set(SCHOOL_A);
        assertThat(new String(storage.retrieve("documents/same-key.pdf"), StandardCharsets.UTF_8))
                .isEqualTo("school A's document");

        TenantContext.set(SCHOOL_B);
        assertThat(new String(storage.retrieve("documents/same-key.pdf"), StandardCharsets.UTF_8))
                .isEqualTo("school B's document");
    }

    @Test
    void deleteIsSilentWhenNothingIsThere(@TempDir Path root) {
        StorageService storage = new FilesystemStorageService(root);
        TenantContext.set(SCHOOL_A);

        storage.delete("documents/never-existed.pdf");
    }

    @Test
    void retrieveAfterDeleteReportsNotFoundRatherThanAnEmptyResult(@TempDir Path root) {
        StorageService storage = new FilesystemStorageService(root);
        TenantContext.set(SCHOOL_A);
        storage.store("documents/one.pdf", "content".getBytes(StandardCharsets.UTF_8), "application/pdf");

        storage.delete("documents/one.pdf");

        assertThatThrownBy(() -> storage.retrieve("documents/one.pdf")).isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void everyOperationRequiresATenantToBeBound(@TempDir Path root) {
        StorageService storage = new FilesystemStorageService(root);

        assertThatThrownBy(() -> storage.store("documents/one.pdf", new byte[] {1}, "application/pdf"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * A relative key is always built by a caller from a generated id and a sniffed extension
     * (ADR-0025), never from user input — but this is the one place that stops being true without
     * anyone noticing, so it is enforced here rather than trusted.
     */
    @Test
    void refusesARelativeKeyThatEscapesItsTenantDirectory(@TempDir Path root) {
        StorageService storage = new FilesystemStorageService(root);
        TenantContext.set(SCHOOL_A);

        assertThatThrownBy(() -> storage.store("../../etc/passwd", new byte[] {1}, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsItsRootDirectoryIfMissing() throws IOException {
        Path notYetCreated = Files.createTempDirectory("chalkbase-storage-test").resolve("nested/does/not/exist");
        new FilesystemStorageService(notYetCreated);

        assertThat(Files.isDirectory(notYetCreated)).isTrue();
    }
}
