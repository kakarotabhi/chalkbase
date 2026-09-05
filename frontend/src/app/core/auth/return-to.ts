/**
 * Validation for the `returnTo` query parameter.
 *
 * `returnTo` is the one place in the app where a value the *browser supplies* is handed straight to
 * `Router.navigateByUrl`. A login form that follows any URL it is given is an open redirect: an
 * attacker mails a staff member `…/login?returnTo=https://chalkbase-login.example`, they sign in on
 * the real site, and the app itself walks them onto the copy. The redirect happens after a genuine
 * sign-in, so nothing looks wrong until the fake page asks for the password again.
 *
 * The rule is therefore a whitelist, not a blacklist: a same-origin absolute path, and nothing else.
 */

/** Control characters and the space, which browsers strip or ignore when resolving a URL. */
const UNSAFE_CHARACTERS = /[\u0000-\u0020\u007f]/;

/**
 * Returns `path` if it is safe to navigate to, or `null` if it is not.
 *
 * Accepted: a single leading `/` followed by a path, e.g. `/fees/receipts?year=2026#top`.
 *
 * Rejected, each for a reason that has been someone's incident report:
 * - `https://evil.example.com` — an absolute URL to another origin.
 * - `//evil.example.com` — protocol-relative; a browser reads it as another origin.
 * - `/\evil.example.com` — browsers normalise the backslash to `/`, making it protocol-relative.
 * - `javascript:…`, `data:…` — not navigation at all.
 * - `fees` — relative, so it resolves against whatever page is current. Ambiguous rather than
 *   dangerous, but there is no reason to accept it.
 * - anything holding a control character or a space, which is how the checks above get smuggled
 *   past a parser that trims later than it validates.
 */
export function safeReturnTo(path: string | null | undefined): string | null {
  if (typeof path !== 'string') {
    return null;
  }

  if (path.length === 0 || UNSAFE_CHARACTERS.test(path)) {
    return null;
  }

  // One slash, and the character after it must not turn the rest into an authority.
  if (path[0] !== '/' || path[1] === '/' || path[1] === '\\') {
    return null;
  }

  return path;
}
