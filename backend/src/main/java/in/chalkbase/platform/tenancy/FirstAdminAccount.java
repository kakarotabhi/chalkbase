package in.chalkbase.platform.tenancy;

import java.util.UUID;

/**
 * The account {@link FirstAdminProvisioner} just created, and the one-time temporary password it
 * must be handed along with.
 *
 * <p>Not a boundary DTO — it never leaves the process as JSON on its own. The caller ({@code
 * school.application.SchoolService}) folds it into {@code school.api.SchoolBootstrapResponse},
 * which carries the {@code @Classification} this record deliberately does not: a plain transport
 * type between two collaborators inside one request is not the same thing as a response body.
 */
public record FirstAdminAccount(UUID accountId, String username, String temporaryPassword) {}
