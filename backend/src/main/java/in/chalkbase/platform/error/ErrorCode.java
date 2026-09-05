package in.chalkbase.platform.error;

import org.springframework.http.HttpStatus;

/**
 * A stable, machine-readable failure identifier.
 *
 * <p>Deliberately an interface rather than one project-wide enum. Each module declares its own
 * codes ({@code SCHOOL_001}, {@code FEE_003}) and the platform declares the cross-cutting ones in
 * {@link PlatformErrorCode}. A single shared enum forces unrelated modules to depend on each other
 * and, in practice, produces things like a loan-specific code being returned for a generic
 * concurrency failure.
 *
 * <p>Codes are API: clients branch on them and schools quote them to support. Renaming one is a
 * breaking change.
 */
public interface ErrorCode {

    /** Stable identifier, e.g. {@code VAL_001}. */
    String code();

    /** Default sentence shown to a user when the throw site does not supply a better one. */
    String defaultMessage();

    /** HTTP status this failure maps to. */
    HttpStatus httpStatus();
}
