package in.chalkbase.platform.error;

/**
 * Maps one database constraint to the message a user should see when it is violated.
 *
 * @param constraintName the constraint exactly as named in the migration, e.g. {@code
 *     uq_school_code}
 * @param errorCode the code returned to the client
 * @param message the sentence shown to the user
 */
public record ConstraintMapping(String constraintName, ErrorCode errorCode, String message) {}
