package in.chalkbase.identity.domain;

/**
 * What a chosen password has to look like.
 *
 * <p>Length carries most of the strength, so the bar is ten characters rather than eight, plus one
 * digit and one symbol. There is deliberately no upper bound below the column width and no
 * character blacklist: rules that force a shape make passwords easier to guess, not harder.
 */
public final class PasswordPolicy {

    public static final int MINIMUM_LENGTH = 10;

    private PasswordPolicy() {}

    public static boolean isAcceptable(String password) {
        if (password == null || password.length() < MINIMUM_LENGTH) {
            return false;
        }
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isDigit(c)) {
                digit = true;
            } else if (!Character.isLetterOrDigit(c)) {
                symbol = true;
            }
        }
        return digit && symbol;
    }
}
