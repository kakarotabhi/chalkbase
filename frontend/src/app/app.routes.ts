import { Routes } from '@angular/router';

/**
 * Top-level routes. One lazy-loaded entry per feature area, named after the backend module it
 * talks to. Keep this file a routing table only — no guards or resolvers inline.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'schools' },
  {
    path: 'schools',
    title: 'Schools',
    loadComponent: () => import('./features/schools/school-list').then((m) => m.SchoolList),
  },
  { path: '**', redirectTo: 'schools' },
];
