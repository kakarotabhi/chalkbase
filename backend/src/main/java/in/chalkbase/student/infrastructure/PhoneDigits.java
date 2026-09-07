package in.chalkbase.student.infrastructure;

import java.util.regex.Pattern;

/**
 * The one place a phone number is turned into digits, for every caller that needs to compare one
 * phone number to another.
 *
 * <p><strong>Why this exists.</strong> The directory search used to compare the raw stored value,
 * so a guardian entered as {@code +919876543210} was never found by a clerk typing
 * {@code 98765 43210} — see {@code guardian.phone_digits} and {@code GuardianService}. The fix was
 * to compare digits to digits, and the rule here is that the digit-stripping half of that fix is
 * written once and reused, not reinvented at the next call site that needs it (ADR-0021's guardian
 * matching being the next one).
 *
 * <p>{@link #digitsOf(String)} is exactly what {@code guardian.phone_digits} computes in the
 * database and what {@code GuardianService}'s directory search applies to a search term — the same
 * transformation, so a number that matches one matches the other.
 *
 * <p>{@link #canonicalKey(String)} is a second, narrower thing built on top of it, and it is not
 * what the human-facing search uses. The search is deliberately an unanchored {@code like}, because
 * a clerk typing a partial number is asking "is there anyone whose number contains this" and a
 * ten-digit local number is a suffix of the same number stored with a country code — substring
 * containment is the right relationship for that question, asked by a person who reads the results.
 *
 * <p>An automated match made during an import has no person reading the results before a guardian
 * is created or a link is written, so containment is the wrong relationship there: two unrelated
 * six-digit landline extensions could each be a substring of the other's neighbour, and nothing
 * would notice. What an import needs is equality — "is this the same number" — answered in a way
 * that still treats {@code +91 98765 43210} and {@code 9876543210} as the same number. Truncating
 * both to their last ten digits before comparing does that for the case this product actually sees
 * (an Indian mobile number, with or without a {@code +91}), and is documented here rather than
 * assumed, because it is a narrower claim than {@link #digitsOf(String)} makes.
 */
public final class PhoneDigits {

    /** Everything that is not 0-9. The one regex; see the class javadoc for why there is one. */
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

    /** How many trailing digits identify an Indian mobile number, regardless of a country code. */
    private static final int LOCAL_LENGTH = 10;

    private PhoneDigits() {}

    /** The digits of a phone number, in order, with everything else removed. Never null. */
    public static String digitsOf(String phone) {
        return phone == null ? "" : NON_DIGIT.matcher(phone).replaceAll("");
    }

    /**
     * The digits that identify this number for an automated, unsupervised match — see the class
     * javadoc for why this is equality on a suffix rather than the search's substring test.
     *
     * <p>Two numbers with the same canonical key are treated as the same guardian's number by the
     * bulk import (ADR-0021). Shorter than ten digits comes back whole, so a number with an
     * obviously incomplete or non-Indian shape does not silently collide with an unrelated one just
     * because both are short.
     */
    public static String canonicalKey(String phone) {
        String digits = digitsOf(phone);
        return digits.length() > LOCAL_LENGTH ? digits.substring(digits.length() - LOCAL_LENGTH) : digits;
    }
}
