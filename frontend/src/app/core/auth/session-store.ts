import { Injectable, computed, signal } from '@angular/core';
import { LoginResponse } from '../api/models';

/**
 * Who is signed in, as signals the shell and the auth screens read.
 *
 * This is memory only. The session itself is the server's cookie; this store just holds what the
 * login response told us so the app can render a name and know whether the forced password change
 * is still outstanding. A reload empties it — which is why the change-password screen sends anyone
 * arriving without a session back to sign in rather than guessing.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly currentUser = signal<LoginResponse | null>(null);

  /**
   * The password just used to sign in, held only for the length of the forced-change flow so the
   * user is not made to retype a temporary password they were handed on a slip of paper. Cleared
   * the moment the change succeeds, and on sign-out. Never written to storage.
   */
  private readonly pendingPassword = signal<string | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly isSignedIn = computed(() => this.currentUser() !== null);
  readonly mustChangePassword = computed(() => this.currentUser()?.mustChangePassword ?? false);
  readonly schoolName = computed(() => this.currentUser()?.school.name ?? null);

  signedIn(user: LoginResponse, passwordUsed: string): void {
    this.currentUser.set(user);
    this.pendingPassword.set(user.mustChangePassword ? passwordUsed : null);
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
  }
}
