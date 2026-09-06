import { Injectable, computed, inject, signal } from '@angular/core';
import { NavigationItem } from '../api/models';
import { IconName } from '../../shared/components/icon/icon-glyphs';
import { navLabel } from './nav-labels';
import { NAV_ROUTES } from './nav-routes';

/** A server navigation item after this app has decided what it points at and what it says. */
export interface NavLink {
  readonly id: string;
  readonly label: string;
  readonly path: string;
  readonly icon: IconName;
  readonly children: readonly NavLink[];
}

/**
 * The menu the shell renders, built from what `GET /api/me` returned.
 *
 * ## This is not access control
 *
 * ADR-0008 is explicit, and it is the oldest bug in this category, so it is written here where the
 * menu is actually built: **hiding a menu item is a convenience, never an authorization control.**
 * Every endpoint enforces its own permission server-side (ADR-0005), and that enforcement is what
 * protects data — anyone can type a URL. It follows that this menu is allowed to be slightly
 * generous, and that nothing downstream may read "the item is not in the menu" as "the user may
 * not do it".
 *
 * ## Unknown ids are dropped
 *
 * The server sends stable ids; `nav-routes.ts` maps them to routes this app owns. An id with no
 * entry cannot be rendered as anything but a dead link, so it is dropped and logged. That is what
 * stops a backend deploy — a new module switched on for a school before the frontend ships its
 * screens — from breaking navigation.
 */
@Injectable({ providedIn: 'root' })
export class NavigationStore {
  private readonly routes = inject(NAV_ROUTES);

  private readonly links = signal<readonly NavLink[]>([]);
  private readonly hasLoaded = signal(false);

  /** Top-level items, in the order the server asked for. Empty until the bootstrap call lands. */
  readonly items = this.links.asReadonly();

  /**
   * Whether a menu has been received. Distinct from "the menu is empty": a user really can have
   * no items, and the shell must tell that apart from "we have not asked yet".
   */
  readonly loaded = this.hasLoaded.asReadonly();

  /**
   * Where a session with no destination of its own belongs: the first thing in *this* user's menu
   * that this build can actually open, or null when there is nothing.
   *
   * The signed-in landing page used to be a constant in `app.routes.ts` — `schools`, then
   * `students` — and both were a guess at what the person signing in is allowed to see. The
   * auditor holds `platform:audit:read` and nothing else, so the guess put a 403 and four console
   * errors in front of them as the first screen after sign-in. The server already answers this
   * question: `/api/me` returns the menu it filtered for this user, and its first item is the
   * first thing they may open. Reading that is reacting to what we were told, not re-deriving the
   * authorization model here, which ADR-0008 forbids.
   *
   * Two details this cannot skip:
   *
   * - **The first item is not always a destination.** `students` is a heading with `students.all`
   *   under it. Landing on a container means landing on whatever its route happens to redirect to,
   *   which is a second guess in a second file; so this descends to the first leaf instead.
   * - **The first item the *server* sent may not be the first item that resolves.** An id with no
   *   entry in `nav-routes.ts` is dropped, along with its children. This reads the resolved tree,
   *   after that filtering, so it can only ever name a path this build owns.
   *
   * Null is a real answer, not a failure to compute: a user whose whole menu was dropped has
   * nowhere to be sent, and inventing a fallback route here would put the bug back.
   */
  readonly landingPath = computed(() => firstDestination(this.links()));

  load(navigation: readonly NavigationItem[]): void {
    this.links.set(this.resolveAll(navigation));
    this.hasLoaded.set(true);
  }

  clear(): void {
    this.links.set([]);
    this.hasLoaded.set(false);
  }

  private resolveAll(items: readonly NavigationItem[]): readonly NavLink[] {
    return [...items]
      .sort(byOrderThenId)
      .map((item) => this.resolve(item))
      .filter((link): link is NavLink => link !== null);
  }

  private resolve(item: NavigationItem): NavLink | null {
    const route = this.routes.get(item.id);
    if (!route) {
      // Logged rather than swallowed: this is the signal that the two sides have drifted, and it
      // is the only place anyone will see it before a user reports a menu item that never existed.
      console.warn(
        `[nav] dropping "${item.id}" — no route is registered for it in nav-routes.ts. ` +
          'The item was returned by /api/me but this build cannot go anywhere with it.',
      );
      return null;
    }

    return {
      id: item.id,
      label: navLabel(item.labelKey),
      path: route.path,
      // The server's `icon` is a hint we deliberately ignore: icons, spacing and animation are the
      // frontend's side of the line (ADR-0008), and honouring a name the design never drew would
      // put pixel decisions in the backend.
      icon: route.icon,
      children: this.resolveAll(item.children ?? []),
    };
  }
}

/**
 * Ascending `order`, ties broken by id.
 *
 * The tie-break is not pedantry: two items sharing an order would otherwise sit wherever the JSON
 * happened to list them, and a menu that reshuffles between page loads is one users stop trusting
 * to be in the same place twice.
 */
function byOrderThenId(a: NavigationItem, b: NavigationItem): number {
  return a.order - b.order || a.id.localeCompare(b.id);
}

/**
 * The first leaf of a resolved menu, depth first.
 *
 * A container falls back to its own path only when nothing resolvable sits under it — its children
 * may all have been dropped, and its own entry is then the closest thing to a destination we have.
 */
function firstDestination(links: readonly NavLink[]): string | null {
  const first = links[0];
  if (!first) {
    return null;
  }
  return firstDestination(first.children) ?? first.path;
}
