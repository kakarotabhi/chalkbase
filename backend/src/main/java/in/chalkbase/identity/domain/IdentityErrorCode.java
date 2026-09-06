package in.chalkbase.identity.domain;

import in.chalkbase.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Failures specific to identity.
 *
 * <p>Note what is <strong>not</strong> here: a wrong password and an unknown username. Both return
 * the platform's {@code AUTH_001} with the same sentence, deliberately. Distinguishing them turns
 * the login form into a tool for discovering which parents are registered at a school.
 */
public enum IdentityErrorCode implements ErrorCode {
    ACCOUNT_LOCKED("AUTH_003", "This account is locked. Ask your school office to unlock it.", HttpStatus.UNAUTHORIZED),
    UNKNOWN_SCHOOL("AUTH_005", "No school with that code", HttpStatus.NOT_FOUND),
    CURRENT_PASSWORD_WRONG("AUTH_006", "The current password is not correct", HttpStatus.BAD_REQUEST),
    WEAK_PASSWORD(
            "AUTH_007",
            "The new password must be at least 10 characters and contain a digit and a symbol",
            HttpStatus.BAD_REQUEST),

    /**
     * The session is real and the credential was correct, but the account is still on the password
     * the school issued it, so it may do nothing except replace that password, read
     * {@code /api/me} and sign out.
     *
     * <p><strong>Why this says what is wrong, where the setup key deliberately does not.</strong>
     * {@code SetupKeyFilter} answers a missing key with a 404 because the caller is a stranger and
     * anything more confirms that an endpoint and a secret exist. Here the caller has already
     * proved they hold this account's credential, and the account is their own. Telling them to
     * change their password is the entire purpose of the flag: the client has to be able to tell
     * this apart from an ordinary {@code PERM_001} — which means "ask your school for this
     * permission" — and send them to the change-password screen instead. There is nothing to hide
     * from someone who is being told a fact about themselves.
     *
     * <p>403 rather than 401: the session is valid and re-authenticating would change nothing.
     * A 401 would make every client clear the session and bounce to the login screen, where the
     * same temporary password would sign them straight back in — a loop.
     */
    PASSWORD_CHANGE_REQUIRED(
            "AUTH_008",
            "Set a new password before continuing. Your school issued this one as a temporary password.",
            HttpStatus.FORBIDDEN);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    IdentityErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
