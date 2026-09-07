package in.chalkbase.platform.storage;

/**
 * A {@link StorageService#retrieve} found no bytes at a key a database row claims to point at
 * (ADR-0025).
 *
 * <p>This is the visible half of the asymmetry ADR-0025 deliberately chose: a delete removes the
 * object before the row, so if the row survives — a concurrent modification, a crash between the
 * two steps — a later download reports this honestly rather than serving nothing silently. It is
 * "the database and the object store disagree", made into a thrown type rather than a null return,
 * so nobody at a call site can forget to check.
 */
public class ObjectNotFoundException extends RuntimeException {

    public ObjectNotFoundException() {
        super("No object was found at the given key");
    }
}
