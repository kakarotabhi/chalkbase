import { Injectable, computed, inject, signal } from '@angular/core';
import { LoginResponse, MeResponse } from '../api/models';
import { NavigationStore } from '../navigation/navigation-store';

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
