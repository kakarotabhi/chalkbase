package in.chalkbase.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How another module resolves a user account it points at, without importing {@code identity}
 * directly or joining across {@code user_account} (ADR-0011, module map).
 *
 * <p>Added for the admission module (Phase 2) — the first feature module to need to point at a
 * <strong>staff</strong> account rather than a student or an academic structure: an enquiry names an
 * assigned counsellor, and {@code admission} has no way to answer "who is that" or "can this account
 * still be assigned something" without this. {@code academics.api.AcademicsLookup} and
 * {@code student.api.StudentLookup} are this interface's precedent — read-only, scoped to the school
 * bound to the request (no school argument anywhere, and one taking one is a review blocker), and
 * answering only what {@link UserSummary} already exposes at {@code GET /api/access/users}: nothing
 * here is a bigger disclosure than that screen already is, so this interface carries no permission of
 * its own — the caller's own module permission (here, {@code admission:enquiry:*}) decides who may
 * reach it, the same way a caller of {@code AcademicsLookup} is gated by its own module's permission
 * rather than {@code academics:class:read}.
 *
 * <p><strong>This is a contract change to this module</strong>, the same sentence
 * {@code StudentLookup} carries for the same reason: {@code identity} did not need a cross-module
 * read interface before admission needed one.
 *
 * <p>Read-only, deliberately, like every interface of this shape: nothing here creates, edits or
 * disables an account. A module that needs the roster changed asks a person to change it in Settings.
 */
public interface IdentityLookup {

    /**
     * Every account in this school currently able to sign in — {@code AccountStatus.ACTIVE} only,
     * by display name.
     *
     * <p>The one list a caller should ever offer for "assign this to someone": a disabled account
     * would otherwise be pickable, silently creating a row nobody can act on.
     */
    List<UserSummary> activeUsers();

    /**
     * Several accounts at once, keyed by id, whichever status they currently hold. An id with no
     * match in this school is simply absent from the map, the same convention every batch method on
     * {@code AcademicsLookup} and {@code StudentLookup} uses.
     *
     * <p>Unlike {@link #activeUsers()}, this does not filter by status: a caller resolving a name to
     * display — an enquiry's already-assigned counsellor, say — has to be able to label the row even
     * if that account has since been disabled. Silently omitting it would read as a deleted row
     * rather than a disabled one.
     */
    Map<UUID, UserSummary> usersOf(Collection<UUID> accountIds);
}
