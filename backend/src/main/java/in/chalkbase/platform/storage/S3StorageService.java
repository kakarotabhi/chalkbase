package in.chalkbase.platform.storage;

import in.chalkbase.platform.tenancy.TenantContext;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * A real {@link StorageService}, backed by an S3-compatible object store — Supabase Storage in
 * development, whatever {@code prod} is pointed at in production (ADR-0025, amended).
 *
 * <p><strong>The tenant prefix is applied here and nowhere else.</strong> {@link #key} prepends the
 * current tenant's schema exactly as {@link FilesystemStorageService#resolve} does, reading
 * {@link TenantContext} rather than accepting a schema from any caller — see this port's own
 * Javadoc for why that is the whole of how one school's files are kept apart from another's, and
 * ADR-0025's tenancy section for what that guarantee does and does not cover for a shared bucket.
 *
 * <p>One shared bucket, not one per tenant (ADR-0025): the bucket name is fixed at construction and
 * every key inside it starts with a schema name, exactly the shape {@code fee_charge} and every
 * other per-tenant table would take if PostgreSQL, too, kept every school in one shared table
 * instead of one schema each — the difference being that a bug here is the only thing enforcing the
 * boundary, where the database enforces its own.
 *
 * <p>{@code S3Client} construction is cheap and makes no network call — nothing here is contacted
 * until the first upload, download or delete, so a misconfigured bucket or an unreachable endpoint
 * never slows or fails application startup. Whatever the SDK's own exception is (a `NoSuchBucket`,
 * a signing failure, a timeout) is folded into {@link ObjectStoreUnavailableException}, the same
 * signal {@code prod} answers with before this adapter exists at all — a caller already handles the
 * "storage is not currently working" case and does not need to be taught a second one.
 */
final class S3StorageService implements StorageService, AutoCloseable {

    private final S3Client client;
    private final String bucket;

    S3StorageService(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public StoredObject store(String relativeKey, byte[] content, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key(relativeKey))
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new ObjectStoreUnavailableException();
        }
        return new StoredObject(relativeKey, content.length, Checksums.sha256(content));
    }

    @Override
    public byte[] retrieve(String relativeKey) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key(relativeKey))
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ObjectNotFoundException();
        } catch (SdkException e) {
            throw new ObjectStoreUnavailableException();
        }
    }

    @Override
    public void delete(String relativeKey) {
        try {
            // S3's own DELETE is already idempotent — deleting a key that is not there answers
            // success, never a 404 — which is exactly the "deleting twice is not an error" contract
            // this port promises, with nothing extra to implement for it here.
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key(relativeKey))
                    .build());
        } catch (SdkException e) {
            throw new ObjectStoreUnavailableException();
        }
    }

    /** Closed by Spring on context shutdown — an inferred destroy method, not a bean of its own. */
    @Override
    public void close() {
        client.close();
    }

    private String key(String relativeKey) {
        String schema = TenantContext.currentSchema()
                .orElseThrow(() -> new IllegalStateException(
                        "No tenant bound. A document's storage is always scoped to one school's schema."));
        return schema + "/" + relativeKey;
    }
}
