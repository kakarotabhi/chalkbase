package in.chalkbase.identity.api;

import in.chalkbase.platform.navigation.NavigationItem;
import java.util.List;

/**
 * Everything a client needs to render the shell, in one call (ADR-0008).
 *
 * <p>One request rather than five, because the alternative is a waterfall on every page load, and
 * these are low-end tablets on a school's broadband. This is the endpoint a browser reload uses;
 * {@code /api/auth/login} stays as it is and returns the smaller answer that a login screen needs.
 *
 * <p>{@link #permissions} and {@link #navigation} are two views of the same decision, and both are
 * conveniences for the interface, never enforcement: every endpoint checks its own permission, and
 * a client that ignored both lists would gain nothing.
 *
 * @param permissionsVersion identifies <em>which</em> permission set this response was built from —
 *     not when. See {@code PermissionsVersion}. Any {@code 403} means the client's view may be
 *     stale: refetch this endpoint, compare, re-render, then show the error.
 * @param permissions the flat list of codes, sorted
 * @param navigation the menu tree, already cut down to this user. Stable dotted ids, never URLs.
 */
public record MeResponse(
        MeUser user,
        SchoolSummary school,
        String permissionsVersion,
        List<String> permissions,
        List<NavigationItem> navigation) {}
