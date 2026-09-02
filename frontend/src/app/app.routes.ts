import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

// Auth + dashboard routes for Phase 2 (docs/architecture/ARCHITECTURE.md §10) - real
// feature routes (assessment, procedures, cases, admin, ...) are lazy-loaded as each
// feature lands, not stubbed out ahead of time. Guards here are UX only (brief §28) -
// every route they protect is independently enforced server-side too.
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./features/auth/verify-email/verify-email').then((m) => m.VerifyEmail),
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    // Phase 3 verification page (docs, not a product route) - proves the reference
    // API end to end through real components; no guard needed, same as the public
    // reference endpoints it calls.
    path: 'reference-demo',
    loadComponent: () => import('./features/reference-demo/reference-demo').then((m) => m.ReferenceDemo),
  },
  {
    // Phase 4 "Browse procedures" - the product's "I know what I need" journey
    // (brief §69), public and unauthenticated like the reference routes above. The
    // "Help me choose" questionnaire is a separate route arriving in Phase 5-7.
    path: 'procedures',
    loadComponent: () => import('./features/procedures/procedure-list/procedure-list').then((m) => m.ProcedureList),
  },
  {
    path: 'procedures/:code',
    loadComponent: () =>
      import('./features/procedures/procedure-detail/procedure-detail').then((m) => m.ProcedureDetailPage),
  },
  {
    path: '**',
    loadComponent: () => import('./features/not-found/not-found').then((m) => m.NotFound),
  },
];
