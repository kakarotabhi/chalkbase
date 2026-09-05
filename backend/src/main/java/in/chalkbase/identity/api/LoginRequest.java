package in.chalkbase.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The login form has three fields, not two.
 *
 * <p>Accounts live inside each school's schema (ADR-0017), so the school must be known before
 * anyone can be authenticated. The frontend remembers the code per device, so a parent types it
 * once.
 */
public record LoginRequest(
        @NotBlank @Size(max = 32) String schoolCode,
        @NotBlank @Size(max = 320) String username,
        @NotBlank @Size(max = 200) String password,
        /**
         * Keeps the session alive for {@code SessionDuration.REMEMBERED} instead of the default.
         * Absent is false — an old client, or a shared machine at the school office, gets the short
         * session.
         */
        Boolean rememberMe) {

    public boolean isRemembered() {
        return Boolean.TRUE.equals(rememberMe);
    }

    /** Keeps the password out of anything that stringifies a request — logs, error messages, traces. */
    @Override
    public String toString() {
        return "LoginRequest[schoolCode=" + schoolCode + "]";
    }
}
