import { ActivatedRoute, Router } from '@angular/router';

/**
 * Mirroring a list screen's filters and page into the URL, the way every list in this app does it.
 *
 * Lives here rather than beside one feature because seven screens now do this — the student list,
 * the guardian directory, leave requests, enquiries, circulars, and the audit log — and the two
 * rules below are exactly the sort of thing that must not be got slightly differently seven times:
 * one screen that pushes a history entry per keystroke, or one that writes `?status=` instead of
 * dropping the key, would work today and read as broken the first time someone actually uses the
 * back button.
 *
 * ## What is never passed here
 *
 * Free text a user typed into a search box, on any of these screens, because it may be a child's
 * name, a guardian's name or a parent's phone number — all Confidential (ADR-0014). Putting it in
 * `queryParams` would mint a URL carrying that text into browser history, a bookmark, a screenshot,
 * and whatever a support ticket pastes. `student-list`, `guardian-list` and `enquiry-list` each
 * explain this beside their own search box; the rule is the same one repeated, not three different
 * ones that happen to agree.
 */

/**
 * Reads `page` off the current URL as a zero-based page index.
 *
 * Never negative, never `NaN`, and absent reads as the first page — the only default that makes
 * "open this screen fresh" and "load a URL with no `page`" the same view.
 */
export function pageFromQueryParams(route: ActivatedRoute): number {
  const raw = Number(route.snapshot.queryParamMap.get('page'));
  return Number.isInteger(raw) && raw > 0 ? raw : 0;
}

/**
 * Writes filter and page state into the URL without adding a history entry for it.
 *
 * Every call **replaces** the current history entry (`replaceUrl: true`) and **merges** rather than
 * overwrites (`queryParamsHandling: 'merge'`), so a filter change or a page turn is never a fresh
 * stop for the back button — only navigating to a different screen is, because that is a real
 * navigation the router already pushes. A caller passes `undefined` for a value that is at its
 * default (an empty filter, page zero); this drops the key from the URL entirely rather than
 * writing it as an empty string, the same `x?: T` rule this app applies to a field on the wire —
 * an absent filter is absent, not present and blank.
 */
export function syncListQueryParams(
  router: Router,
  route: ActivatedRoute,
  params: Readonly<Record<string, string | number | undefined>>,
): void {
  const queryParams: Record<string, string | number | null> = {};
  for (const [key, value] of Object.entries(params)) {
    queryParams[key] = value === undefined || value === '' ? null : value;
  }
  void router.navigate([], {
    relativeTo: route,
    queryParams,
    queryParamsHandling: 'merge',
    replaceUrl: true,
  });
}
