package in.chalkbase.platform.storage;

/**
 * Where a module puts bytes it does not want to keep in a database column (ADR-0025).
 *
 * <p><strong>{@code relativeKey} names a place inside the current tenant, never the tenant
 * itself.</strong> A caller never states a school, a bucket or a prefix — the adapter behind this
 * interface reads {@link in.chalkbase.platform.tenancy.TenantContext} and prepends whatever the
 * tenant boundary actually is, the same way {@code SchemaMultiTenantConnectionProvider} resolves a
 * database connection's {@code search_path} without a caller ever naming a schema. This is the
 * whole of how one school's files are kept from another's: not a convention callers must remember,
 * but a value they are never given the chance to supply. See ADR-0025's tenancy section for what
 * that does and does not protect against.
 *
 * <p>Every method requires a tenant to be bound, exactly as {@code AuditService.recordChange} does,
 * and for the same reason: a file with nowhere to be scoped is not a case this port has an answer
 * for.
 *
 * <p>Content lives in memory as {@code byte[]} on both sides of this interface rather than as a
 * stream, deliberately: {@code document}'s upload size cap (ADR-0025) is small enough that this
 * costs nothing and buys a port with no lifecycle to manage — no stream left unclosed by a caller
 * that forgot, and no adapter that has to decide when to buffer anyway to compute a checksum before
 * it can even start sending.
 */
public interface StorageService {

    /**
     * Writes {@code content} and returns what actually got written.
     *
     * @param relativeKey where inside this tenant, e.g. {@code "documents/<id>.pdf"}. Never
     *     supplied by anything derived from a user-typed filename — see {@code document}'s
     *     upload path for why.
     * @param content the bytes, never null, never empty — callers reject an empty upload before
     *     this is called, so an adapter never has to decide what an empty object means.
     * @param contentType the sniffed content type, never the client's declared one
     * @throws ObjectStoreUnavailableException if this deployment has no working adapter (ADR-0025)
     */
    StoredObject store(String relativeKey, byte[] content, String contentType);

    /**
     * Reads back exactly what {@link #store} wrote.
     *
     * @throws ObjectStoreUnavailableException if this deployment has no working adapter
     * @throws ObjectNotFoundException if nothing is stored at {@code relativeKey} in this tenant —
     *     the row pointing at it survived a delete that removed the bytes but not the row (ADR-0025)
     */
    byte[] retrieve(String relativeKey);

    /**
     * Removes whatever is at {@code relativeKey}, or does nothing if there was never anything
     * there — deleting twice is not an error, so a caller retrying a failed delete never has to
     * check first.
     *
     * @throws ObjectStoreUnavailableException if this deployment has no working adapter
     */
    void delete(String relativeKey);
}
