package in.chalkbase.school.api;

import in.chalkbase.school.domain.Board;
import in.chalkbase.school.domain.School;
import in.chalkbase.school.domain.SchoolProfile;
import java.time.Instant;

/**
 * The current school, as its own administrators see it.
 *
 * <p>Two sources, joined here rather than in the client: {@code code} and {@code schemaName} come
 * from the registry row in {@code public.school} and cannot be edited, {@code name} comes from the
 * registry and can, and everything else is the school's own profile row inside its schema.
 *
 * <p>There is no id and no path parameter anywhere near this: the tenant <em>is</em> the school, so
 * "which school" is answered by the session (ADR-0011), never by the request.
 *
 * @param code how the school is addressed on the sign-in form. Immutable.
 * @param schemaName the PostgreSQL schema holding this school's data. Immutable. Returned so a
 *     client can echo it back and be told plainly if it tried to change it.
 * @param configured false when no profile row exists yet, i.e. nothing below has been filled in.
 *     The screen uses it to say "complete your profile" rather than showing an empty form as if
 *     something had been lost.
 * @param updatedAt when the profile was last saved, or null when it never has been
 */
public record SchoolProfileResponse(
        String code,
        String schemaName,
        String name,
        Board board,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String principalName,
        String phone,
        String email,
        String website,
        String affiliationNumber,
        boolean configured,
        Instant updatedAt) {

    public static SchoolProfileResponse of(School school, SchoolProfile profile) {
        if (profile == null) {
            // A school with no profile row is not an error and is not empty either: the registry
            // already knows its name, board and town, so those are seeded into the form rather than
            // asked for a second time.
            return new SchoolProfileResponse(
                    school.getCode(),
                    school.getSchemaName(),
                    school.getName(),
                    school.getBoard(),
                    null,
                    null,
                    school.getCity(),
                    school.getState(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null);
        }
        return new SchoolProfileResponse(
                school.getCode(),
                school.getSchemaName(),
                school.getName(),
                profile.getBoard(),
                profile.getAddressLine1(),
                profile.getAddressLine2(),
                profile.getCity(),
                profile.getState(),
                profile.getPincode(),
                profile.getPrincipalName(),
                profile.getPhone(),
                profile.getEmail(),
                profile.getWebsite(),
                profile.getAffiliationNumber(),
                true,
                profile.getUpdatedAt());
    }
}
