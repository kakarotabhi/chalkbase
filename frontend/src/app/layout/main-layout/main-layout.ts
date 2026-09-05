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
  protected readonly navItems = [{ path: '/schools', label: 'Schools' }] as const;
}
