import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Application shell: header, primary navigation and the routed content area.
 *
 * Navigation is a placeholder — it becomes permission-driven once the identity module exposes the
 * current user's permissions.
 */
@Component({
  selector: 'cb-main-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  // Placeholder. Navigation is served by the backend from /api/v1/me once the identity module
  // exists (ADR-0008), including the compact-width "More" sheet when a user has more top-level
  // items than a bottom bar can hold.
  protected readonly navItems = [{ path: '/schools', label: 'Schools', icon: '🏫' }] as const;
}
