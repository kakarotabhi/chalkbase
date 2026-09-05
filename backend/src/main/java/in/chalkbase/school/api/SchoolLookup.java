package in.chalkbase.school.api;

import java.util.Optional;

/**
 * Resolves a school code to its tenant schema.
 *
 * <p>Exists because identity has to know which schema to bind before it can look up a user
 * (ADR-0017), and the registry belongs to this module. Other modules call this rather than reading
 * {@code public.school} themselves.
 */
public interface SchoolLookup {

    /** The active school with this code, or empty. Inactive schools are not resolvable. */
    Optional<SchoolRef> byCode(String code);
}
