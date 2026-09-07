package in.chalkbase.platform.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Lets the module that owns authentication say which account is currently acting, as a plain id.
 *
 * <p>Mirrors {@code platform.audit.AuditActorResolver} exactly, and exists for the same reason:
 * the shared kernel must not import a feature module, so {@code identity} registers this instead
 * of the platform (or another module) reaching in for {@code AuthenticatedUser}.
 *
 * <p>{@code AuditActorResolver} already answers "who, for the log" with a name and a role
 * snapshot — enough to describe an event, never enough to be a foreign key. This is for the other
 * case: a module that needs to <strong>store</strong> who acted, as a {@code uuid} column a later
 * read can resolve back to an account. Attendance is the first: a mark's {@code marked_by} and a
 * correction's {@code requested_by}/{@code decided_by} are domain data, not audit rows, and ADR-0018
 * §3 already draws that line — {@code changedFields} names never carry values, so "who did this"
 * has to live on the record itself wherever the record needs to answer it later, the same way
 * {@code user_account.password_reset_by} already does inside {@code identity}.
 *
 * <p>Register one per module that can establish a principal, as a {@code @Bean} inside that
 * module. There is exactly one today; {@link CurrentUser} asks each in turn and takes the first
 * answer, so a second authentication mechanism is a new bean and nothing else.
 *
 * <p>An implementation must return {@link Optional#empty()} rather than throwing when nobody is
 * authenticated — a scheduled job is the normal case, not an error condition.
 */
@FunctionalInterface
public interface CurrentUserResolver {

    Optional<UUID> currentUserId();
}
