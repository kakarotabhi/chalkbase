import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { LoginResponse, MeResponse } from '../api/models';
import { NavigationStore } from '../navigation/navigation-store';
import { Permission } from './permissions';

/**
 * Who is signed in, as signals the shell and the auth screens read.
 *
 * This is memory only. The session itself is the server's cookie; this store just holds what the
 * server told us so the app can render a name and know whether the forced password change is
 * still outstanding. A reload empties it, and `SessionBootstrap` fills it again from
 * `GET /api/me` — the server is the authority on whether there is a session at all.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  /**
   * The menu belongs to the session, so it is emptied on the same transitions.
   *
   * Signing in is one of them, not only signing out: a shared computer at the school office sees
   * one person sign out and the next sign in, and a menu left over from the previous session would
   * be the second person's first impression of the product. Emptying it here is also what tells
   * `SessionBootstrap` there is a fresh `/api/me` to fetch.
   */
  private readonly navigation = inject(NavigationStore);

  private readonly currentUser = signal<LoginResponse | null>(null);

  /**
   * The password just used to sign in, held only for the length of the forced-change flow so the
   * user is not made to retype a temporary password they were handed on a slip of paper. Cleared
   * the moment the change succeeds, and on sign-out. Never written to storage.
   */
  private readonly pendingPassword = signal<string | null>(null);

  /**
   * The `permissionsVersion` the bootstrap response carried, or null before one has landed.
   *
   * Effective permissions are resolved once per session (ADR-0005), so a role change mid-session
   * leaves this tab holding a stale menu. This is the value that will tell it so.
   */
  private readonly currentPermissionsVersion = signal<string | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly permissionsVersion = this.currentPermissionsVersion.asReadonly();
  readonly isSignedIn = computed(() => this.currentUser() !== null);
  readonly mustChangePassword = computed(() => this.currentUser()?.mustChangePassword ?? false);
  readonly schoolName = computed(() => this.currentUser()?.school.name ?? null);

  /**
   * What this user may do, as a set, recomputed whenever the session changes.
   *
   * Both ways in fill it: the login response carries `permissions` and so does `GET /api/me`, so
   * this is populated on the sign-in path and on the reload path without either of them knowing
   * about this signal. Signing out empties it, because `currentUser` goes null.
   *
   * A `Set` rather than the array the server sent, so a screen with a dozen gated controls does a
   * dozen hash lookups rather than a dozen linear scans, and so nothing downstream can mutate the
   * list the store is holding.
   */
  private readonly grantedPermissions = computed<ReadonlySet<string>>(
    () => new Set(this.currentUser()?.permissions ?? []),
  );

  /**
   * Whether this user holds a permission — the one question the interface asks about access.
   *
   * **Not a boundary.** ADR-0005: the server enforces every permission independently and this
   * decides only what to draw. The value of drawing it correctly is that somebody is not asked to
   * fill in a form that ends in a 403.
   *
   * **Absent means no.** A store with no session holds no permissions, so every gated control is
   * hidden. That is the failure this app wants: the alternative — show everything when we do not
   * know — is precisely the defect this replaced, and the only state in which the list is empty
   * for a real user is one where they are not signed in and a route guard is already sending them
   * to the sign-in screen. A network failure during bootstrap does not land here either:
   * `SessionBootstrap` keeps whatever the tab already knew rather than emptying the store.
   *
   * Reading a signal, so a caller inside a `computed` or a template re-evaluates when the session
   * changes — a permission read once at construction would still be showing the previous user's
   * buttons after a sign-out and a sign-in on the school office's shared machine.
   */
  has(permission: Permission): boolean {
    return this.grantedPermissions().has(permission);
  }

  signedIn(user: LoginResponse, passwordUsed: string): void {
    this.currentUser.set(user);
    this.pendingPassword.set(user.mustChangePassword ? passwordUsed : null);
    this.navigation.clear();
  }

  /**
   * Fills the store from the bootstrap call rather than from a login response.
   *
   * This is the path a reload takes: there is a session cookie, nobody typed a password, and
   * everything the shell needs comes back from `GET /api/me`. The password held for the forced
   * change flow is deliberately left alone — an existing one belongs to the sign-in that is still
   * in progress, and there is never a new one to record here.
   */
  bootstrapped(me: MeResponse): void {
    this.currentUser.set({
      userId: me.user.id,
      displayName: me.user.displayName,
      mustChangePassword: me.user.mustChangePassword,
      school: me.school,
      permissions: me.permissions,
    });
    this.currentPermissionsVersion.set(me.permissionsVersion);
  }

  /** The temporary password to send as `currentPassword`, or null if the flow was not entered. */
  temporaryPassword(): string | null {
    return this.pendingPassword();
  }

  passwordChanged(): void {
    this.pendingPassword.set(null);
    const user = this.currentUser();
    if (user) {
      this.currentUser.set({ ...user, mustChangePassword: false });
    }
  }

  signedOut(): void {
    this.currentUser.set(null);
    this.pendingPassword.set(null);
    this.currentPermissionsVersion.set(null);
    this.navigation.clear();
  }
}

/**
 * The one way a component asks whether this user may do something.
 *
 * ```ts
 * protected readonly canManageStudents = permitted(Permissions.STUDENT_MANAGE);
 * ```
 * ```html
 * @if (canManageStudents()) { <cb-button …>Add a student</cb-button> }
 * ```
 *
 * A field initialiser in an injection context, like `inject` itself, so there is no store to hold
 * and no permission string in a template. The signal it returns is live: it tracks the session
 * rather than sampling it, so nothing has to be re-created when the signed-in user changes.
 *
 * Deliberately not a structural directive. This codebase moved to `@if` control flow and forbids
 * `*ngIf`, so a new `*`-prefixed directive would be the one exception to that rule in the whole
 * app; and a directive can only gate a whole element, where several screens here need to gate a
 * group of controls that already sit inside a `@if` on something else.
 */
export function permitted(permission: Permission): Signal<boolean> {
  const session = inject(SessionStore);
  return computed(() => session.has(permission));
}
