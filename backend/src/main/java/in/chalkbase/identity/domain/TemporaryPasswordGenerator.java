package in.chalkbase.identity.domain;

import java.security.SecureRandom;

/**
 * A school-issued password, generated rather than chosen — for a new account, and for an admin
 * password reset.
 *
 * <p>Satisfies {@link PasswordPolicy} by construction: one letter from each case, one digit, one
 * symbol, and enough further random characters to clear the length floor, then shuffled so the
 * fixed categories are not always in the same position. There is no dictionary check and no
 * memorability goal — unlike a password someone will type for months, this one is read out once,
 * typed once, and immediately replaced by {@code must_change_password}.
 */
public final class TemporaryPasswordGenerator {

    private static final int LENGTH = 12;
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*-_=+";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswordGenerator() {}

    public static String generate() {
        char[] password = new char[LENGTH];
        password[0] = pick(UPPER);
        password[1] = pick(LOWER);
        password[2] = pick(DIGITS);
        password[3] = pick(SYMBOLS);
        for (int i = 4; i < LENGTH; i++) {
            password[i] = pick(ALL);
        }
        shuffle(password);
        String generated = new String(password);
        // A construction proof, not a runtime possibility: this would only fail if the alphabets
        // above were edited incorrectly.
        assert PasswordPolicy.isAcceptable(generated) : "generated password does not satisfy PasswordPolicy";
        return generated;
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    private static void shuffle(char[] chars) {
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char swap = chars[i];
            chars[i] = chars[j];
            chars[j] = swap;
        }
    }
}
