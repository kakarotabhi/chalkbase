/**
 * Identity: who may sign in to a school, and how they prove it.
 *
 * <p>Accounts, identifiers and credentials are per-tenant tables (ADR-0017) — nothing about a
 * person lives in {@code public}. The one exception is the session store, which has to be in
 * {@code public} because reading the session cookie is what tells us which tenant to bind.
 *
 * <p>Identity is separated from proof (ADR-0003): {@code user_identifier} says who someone is,
 * {@code user_credential} says how they prove it, and a {@code CredentialVerifier} per credential
 * type does the proving. Adding phone + OTP later is a new verifier and two rows, not a migration.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package in.chalkbase.identity;
