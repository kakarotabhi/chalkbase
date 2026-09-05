package in.chalkbase.school.api;

/**
 * The minimum another module needs to know about a school: how it is addressed, what to call it,
 * and which PostgreSQL schema its data lives in.
 *
 * <p>Deliberately not {@link SchoolResponse} — that is a read model for HTTP clients and will grow
 * fields. This is the cross-module contract and stays small.
 *
 * @param code the code a user types, e.g. on the login form
 * @param name the school's display name
 * @param schemaName the tenant schema to bind before touching that school's data (ADR-0011)
 */
public record SchoolRef(String code, String name, String schemaName) {}
