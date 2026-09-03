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
    // Phase 5 "Help me choose" (brief §47) - authenticated-only (brief §32), same
    // guard every other logged-in-only route uses. /start resolves/creates the
    // caller's assessment then redirects to /assessment/:id, the real wizard route;
    // review and completion are steps within that one component, not separate
    // routes (brief §47's "or a simpler coherent route structure" - see
    // PHASE_5_REPORT.md "Deviations").
    path: 'assessment/start',
    canActivate: [authGuard],
    loadComponent: () => import('./features/assessment/assessment-start/assessment-start').then((m) => m.AssessmentStart),
  },
  {
    path: 'assessment/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/assessment/assessment-wizard/assessment-wizard').then((m) => m.AssessmentWizard),
  },
  {
    // Phase 7 "Analyze my pathways" results (brief §88) - authenticated-only like the
    // assessment routes above; ownership is independently enforced server-side.
    path: 'assessment/:id/results',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/recommendations/recommendation-results/recommendation-results').then(
        (m) => m.RecommendationResults,
      ),
  },
  {
    path: '**',
    loadComponent: () => import('./features/not-found/not-found').then((m) => m.NotFound),
  },
];
