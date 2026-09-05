import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionStore } from '../../core/auth/session-store';
import { UserMenu } from '../user-menu/user-menu';

/**
 * Application shell: header, primary navigation and the routed content area.
 *
 * Navigation is a placeholder — it becomes permission-driven once the identity module exposes the
 * current user's permissions.
 */
@Component({
  selector: 'cb-main-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, UserMenu],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  private readonly session = inject(SessionStore);

  /**
   * The school whose data is on screen. Staff at a group of schools sign in one school at a time,
   * so this label is what says which set of children the numbers below belong to.
   *
   * The fallback is the product name, not a blank or a skeleton: the store is empty only before
   * the login response lands or after a reload, and an empty header reads as breakage.
   */
  protected readonly schoolName = computed(() => this.session.schoolName() ?? 'Chalkbase');

  // Placeholder. Navigation is served by the backend from /api/me once the identity module
  // exists (ADR-0008), including the compact-width "More" sheet when a user has more top-level
  // items than a bottom bar can hold.
  protected readonly navItems = [{ path: '/schools', label: 'Schools', icon: '🏫' }] as const;
}
