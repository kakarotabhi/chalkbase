package in.chalkbase.school.api;

import in.chalkbase.school.domain.Board;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A full replacement of the current school's profile.
 *
 * <p>A whole form, not a patch. Every editable field is sent every time, so a screen that forgets
 * one cannot silently blank it and a missing field is a validation failure with a name attached
 * rather than a null in the database.
 *
 * <p>{@code code} and {@code schemaName} are here <strong>so that changing them can be refused</strong>.
 * They are what addresses the tenant and names its schema (ADR-0011), a client that sends a
 * different one is confused about something important, and answering "saved" while quietly keeping
 * the old value is how that confusion survives to production. Send them unchanged, or leave them
 * out.
 *
 * <p>The patterns below are duplicated in the school profile form and, more loosely, as check
 * constraints on {@code school_profile}. That is deliberate: the form catches a mistake before the
 * round trip, this rejects it for every other client, and the database is what holds when something
 * writes without going through here.
 */
public record UpdateSchoolProfileRequest(
        /** Echoed back unchanged, or omitted. Any other value is rejected — it cannot be changed. */
        @Size(max = 32) String code,
        /** Echoed back unchanged, or omitted. Any other value is rejected — it cannot be changed. */
        @Size(max = 63) String schemaName,
        @NotBlank @Size(max = 200) String name,
        @NotNull Board board,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,

        @NotBlank @Pattern(
                regexp = UpdateSchoolProfileRequest.PINCODE_PATTERN,
                message = "must be six digits and must not start with a zero")
        String pincode,

        @NotBlank @Size(max = 200) String principalName,

        @NotBlank @Pattern(
                regexp = UpdateSchoolProfileRequest.PHONE_PATTERN,
                message = "must be 7 to 20 characters of digits, spaces, brackets or dashes")
        String phone,

        @NotBlank @Email @Size(max = 320) String email,

        @Pattern(regexp = UpdateSchoolProfileRequest.WEBSITE_PATTERN, message = "must start with http:// or https://") @Size(max = 200)
        String website,

        @Size(max = 40) String affiliationNumber) {

    /** Six digits, never leading zero — an Indian PIN code. */
    public static final String PINCODE_PATTERN = "^[1-9][0-9]{5}$";

    /**
     * Loose on purpose. Indian school numbers are written as landlines with STD codes, as mobiles,
     * with and without +91, and a validator strict enough to have an opinion about that rejects
     * numbers that work.
     */
    public static final String PHONE_PATTERN = "^[+0-9][0-9 ()-]{6,19}$";

    /** A scheme is required: a link a parent taps has to be one a browser can follow. */
    public static final String WEBSITE_PATTERN = "^https?://\\S+$";
}
