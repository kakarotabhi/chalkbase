import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth-guard';
import { unsavedChangesGuard } from './core/forms/unsaved-changes-guard';

/**
 * Top-level routes.
 *
 * Two groups. The auth screens stand alone: sign-in is the one screen with no navigation, because
 * there is nowhere to go until you are through it, and a shell around it would only offer links
 * that cannot be followed. Everything else is a child of `MainLayout`, which supplies the header
 * and the primary navigation once.
 *
 * Keep this file a routing table only — no guards or resolvers inline.
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · Chalkbase',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'change-password',
    title: 'Set a new password · Chalkbase',
    loadComponent: () =>
      import('./features/auth/change-password/change-password').then((m) => m.ChangePassword),
  },
  {
    path: '',
    // One guard on the shell rather than one per feature: everything inside it needs a session,
    // and a list that has to be added to is a list someone forgets.
    canActivate: [authGuard],
    loadComponent: () => import('./layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'schools' },
      {
        path: 'schools',
        title: 'Schools',
        loadComponent: () => import('./features/schools/school-list').then((m) => m.SchoolList),
      },
      // No guard, deliberately. ADR-0008 is explicit that a menu is a convenience and never an
      // authorization control, and warns against re-deriving the authorization model client-side —
      // which a `canActivate` checking `platform:audit:read` would be. The server already leaves
      // the item out of the menu for anyone without it, and `GET /api/audit` enforces it. So
      // someone who types the URL gets a 403 the screen explains, not a redirect that pretends the
      // page does not exist.
      {
        path: 'audit',
        title: 'Audit log · Chalkbase',
        loadComponent: () => import('./features/audit/audit-log').then((m) => m.AuditLog),
      },
      // Settings has no index of its own yet, so the section lands on the one screen it has. When
      // a second settings screen ships this becomes a real index and the redirect goes.
      { path: 'settings', pathMatch: 'full', redirectTo: 'settings/school-profile' },
      {
        path: 'settings/school-profile',
        title: 'School profile · Chalkbase',
        // The only guard on a child of the shell, and it earns its place: this is the first screen
        // in Chalkbase where navigating away loses typed work.
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/schools/school-profile').then((m) => m.SchoolProfile),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
