package in.chalkbase.identity.domain;

/**
 * How a person is named at sign-in.
 *
 * <p>The login identifier is a row rather than a column on the account (ADR-0003), so adding phone
 * login later needs no change to {@code user_account}.
 */
public enum IdentifierType {
    USERNAME,
    EMAIL,
    PHONE
}
