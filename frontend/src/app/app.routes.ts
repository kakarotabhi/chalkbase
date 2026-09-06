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
      // Students, not the school register. The register is a platform-operator view and is no
      // longer in a school user's menu at all — landing there meant the first screen after signing
      // in was an error for every user, which is how it was found. Students is what a school
      // actually opens the product to do.
      { path: '', pathMatch: 'full', redirectTo: 'students' },
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
      // Academics: the school's own academic model — its years, and its ladder of classes and
      // sections (ADR-0019). No guard on either, for the same reason the audit log has none.
      //
      // The container has no index screen of its own, so it lands on the sessions list: a class
      // means little until there is a year to enrol anybody into.
      { path: 'academics', pathMatch: 'full', redirectTo: 'academics/sessions' },
      {
        path: 'academics/sessions',
        title: 'Academic sessions · Chalkbase',
        loadComponent: () =>
          import('./features/academics/academic-sessions').then((m) => m.AcademicSessions),
      },
      {
        path: 'academics/classes',
        title: 'Classes and sections · Chalkbase',
        loadComponent: () =>
          import('./features/academics/school-classes').then((m) => m.SchoolClasses),
      },
      // Students, and the record behind them (ADR-0020). No guard on any of the three, for the
      // same reason the audit log has none: ADR-0008 puts authorization on the server, and a
      // `canActivate` checking `student:student:read` would be a second copy of it.
      //
      // **Order matters here.** `students/guardians` and `students/import` are declared before
      // `students/:id`, because the router matches in order and `:id` would otherwise swallow the
      // word "guardians" and try to load a student whose id is that. This is the kind of thing that
      // works until somebody reorders the file for tidiness, so it is written down.
      {
        path: 'students',
        pathMatch: 'full',
        title: 'Students · Chalkbase',
        loadComponent: () => import('./features/students/student-list').then((m) => m.StudentList),
      },
      {
        path: 'students/guardians',
        title: 'Guardians · Chalkbase',
        loadComponent: () =>
          import('./features/students/guardian-list').then((m) => m.GuardianList),
      },
      {
        path: 'students/import',
        title: 'Import students · Chalkbase',
        loadComponent: () =>
          import('./features/students/student-import').then((m) => m.StudentImport),
      },
      {
        path: 'students/:id',
        // "Student record", never the child's name. A title goes into the window manager, the
        // browser history and every screenshot of this screen, and a name is Confidential
        // (ADR-0014). The route carries a UUID for the same reason.
        title: 'Student record · Chalkbase',
        loadComponent: () =>
          import('./features/students/student-detail').then((m) => m.StudentDetail),
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
