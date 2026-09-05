import { Routes } from '@angular/router';

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
    loadComponent: () => import('./layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'schools' },
      {
        path: 'schools',
        title: 'Schools',
        loadComponent: () => import('./features/schools/school-list').then((m) => m.SchoolList),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
