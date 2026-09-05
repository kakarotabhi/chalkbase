package in.chalkbase.platform.security;

/**
 * One permission, declared in code by the module that enforces it (ADR-0005).
 *
 * <p>The identifier is {@code <module>:<resource>:<action>} — {@code fee:invoice:create},
 * {@code school:school:read}. It is effectively public API: a school's saved roles store these
 * strings, so renaming one needs a migration that rewrites every school's {@code role_permission}
 * rows.
 *
 * <p>{@code label} and {@code description} are not decoration. A principal reads this list in a UI
 * when building a role, so every entry has to mean something to someone who has never seen the
 * code.
 *
 * @param code the identifier, which must begin with {@code module + ":"}
 * @param module the owning application module, e.g. {@code school}
 * @param label a short human-readable name, e.g. "View schools"
 * @param description one sentence saying what holding this permission lets someone do
 */
public record PermissionDefinition(String code, String module, String label, String description) {}
