package in.chalkbase.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.chalkbase.platform.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * {@link S3StorageService} against a mocked {@link S3Client} — no bucket, no network and no
 * credentials, the same trade {@code FilesystemStorageServiceTests} makes for the filesystem
 * adapter. What is under test here is this adapter's own contribution over the SDK: the tenant
 * prefix applied to every key (ADR-0025's tenancy section names this class as the highest-value
 * one to review carefully), and the mapping from the SDK's own exceptions to this port's.
 */
class S3StorageServiceTests {

    private static final String SCHOOL_A = "riverbank_documents";
    private static final String SCHOOL_B = "cloverdale_documents";
    private static final String BUCKET = "chalkbase-documents";

    private final S3Client client = mock(S3Client.class);
    private final StorageService storage = new S3StorageService(client, BUCKET);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void storePrefixesTheKeyWithTheCurrentTenantAndReturnsWhatWasWritten() {
        TenantContext.set(SCHOOL_A);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        StoredObject stored = storage.store("documents/one.pdf", content, "application/pdf");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(SCHOOL_A + "/documents/one.pdf");
        assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");

        // The relative key comes back exactly as it was passed in — the tenant prefix is this
        // adapter's own affair and never something a caller supplied or should see again.
        assertThat(stored.relativeKey()).isEqualTo("documents/one.pdf");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
    }

    @Test
    void retrieveReadsBackFromTheTenantPrefixedKey() {
        TenantContext.set(SCHOOL_A);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

        assertThat(storage.retrieve("documents/one.pdf")).isEqualTo(content);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(SCHOOL_A + "/documents/one.pdf");
    }

    @Test
    void retrieveMapsNoSuchKeyToObjectNotFound() {
        TenantContext.set(SCHOOL_A);
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> storage.retrieve("documents/missing.pdf")).isInstanceOf(ObjectNotFoundException.class);
    }

    /**
     * Whatever the SDK's own failure is — a bad signature, a timeout, an unreachable endpoint — a
     * caller sees the one signal this port already defines for "storage is not currently working",
     * not a new one it would have to learn to handle.
     */
    @Test
    void everyOtherSdkFailureBecomesObjectStoreUnavailable() {
        TenantContext.set(SCHOOL_A);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.builder().message("timed out").build());

        assertThatThrownBy(() -> storage.store("documents/one.pdf", new byte[] {1}, "application/pdf"))
                .isInstanceOf(ObjectStoreUnavailableException.class);
    }

    @Test
    void deletePrefixesTheKeyToo() {
        TenantContext.set(SCHOOL_A);

        storage.delete("documents/one.pdf");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(SCHOOL_A + "/documents/one.pdf");
    }

    /**
     * The claim ADR-0025's tenancy section rests its whole argument on, exercised here the same way
     * {@code FilesystemStorageServiceTests} exercises it for the filesystem adapter: the same
     * relative key, written by two different tenants, becomes two different object keys in the one
     * shared bucket.
     */
    @Test
    void twoTenantsWritingTheSameRelativeKeyResolveToDifferentObjectKeys() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        TenantContext.set(SCHOOL_A);
        storage.store("documents/same-key.pdf", new byte[] {1}, "application/pdf");
        TenantContext.set(SCHOOL_B);
        storage.store("documents/same-key.pdf", new byte[] {2}, "application/pdf");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, times(2)).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getAllValues())
                .extracting(PutObjectRequest::key)
                .containsExactly(SCHOOL_A + "/documents/same-key.pdf", SCHOOL_B + "/documents/same-key.pdf");
    }

    @Test
    void everyOperationRequiresATenantToBeBound() {
        assertThatThrownBy(() -> storage.store("documents/one.pdf", new byte[] {1}, "application/pdf"))
                .isInstanceOf(IllegalStateException.class);
    }
}
