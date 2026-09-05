package in.chalkbase.identity.domain;

/** Whether an account may be used at all. Lockout is separate and temporary. */
public enum AccountStatus {
    ACTIVE,
    DISABLED
}
