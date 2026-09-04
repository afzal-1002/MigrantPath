import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
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
    data: { noIndex: true },
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    data: { noIndex: true },
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'verify-email',
    data: { noIndex: true },
    loadComponent: () => import('./features/auth/verify-email/verify-email').then((m) => m.VerifyEmail),
  },
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    data: { noIndex: true },
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    canActivate: [guestGuard],
    data: { noIndex: true },
    loadComponent: () => import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    data: { noIndex: true },
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    // Phase 3 verification page (docs, not a product route) - proves the reference
    // API end to end through real components; no guard needed, same as the public
    // reference endpoints it calls. Not indexed - a dev/QA verification aid, not
    // real product content (brief §93).
    path: 'reference-demo',
    data: { noIndex: true },
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
    data: { noIndex: true },
    loadComponent: () => import('./features/assessment/assessment-start/assessment-start').then((m) => m.AssessmentStart),
  },
  {
    path: 'assessment/:id',
    canActivate: [authGuard],
    data: { noIndex: true },
    loadComponent: () =>
      import('./features/assessment/assessment-wizard/assessment-wizard').then((m) => m.AssessmentWizard),
  },
  {
    // Phase 7 "Analyze my pathways" results (brief §88) - authenticated-only like the
    // assessment routes above; ownership is independently enforced server-side.
    path: 'assessment/:id/results',
    canActivate: [authGuard],
    data: { noIndex: true },
    loadComponent: () =>
      import('./features/recommendations/recommendation-results/recommendation-results').then(
        (m) => m.RecommendationResults,
      ),
  },
  {
    // Canonical Phase 12 (Security/Privacy/GDPR) self-service privacy page (brief §26/§51) -
    // authenticated-only; every action it exposes operates only on the caller's own account
    // server-side (brief §74), same ownership discipline as every other private route.
    path: 'account',
    canActivate: [authGuard],
    data: { noIndex: true },
    loadComponent: () => import('./features/account/account').then((m) => m.Account),
  },
  {
    // Phase 8 "My Cases" (brief §40/§41) - authenticated-only; ownership is independently
    // enforced server-side.
    path: 'cases',
    canActivate: [authGuard],
    data: { noIndex: true },
    loadComponent: () => import('./features/cases/case-list/case-list').then((m) => m.CaseList),
  },
  {
    path: 'cases/:id',
    canActivate: [authGuard],
    data: { noIndex: true },
    loadComponent: () => import('./features/cases/case-detail/case-detail').then((m) => m.CaseDetailPage),
  },
  {
    // Phase 9 admin panel (brief §14/§15) - authGuard first (must be logged in at all),
    // adminGuard second (must hold an admin role); every child route is lazy-loaded, same
    // convention as the rest of this file. Server-side authorization is what actually
    // matters (SecurityConfig) - both guards here are UX only. `noIndex` here covers
    // every child route too (RobotsMetaService walks the whole ancestor chain).
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    data: { noIndex: true },
    loadComponent: () => import('./features/admin/admin-shell/admin-shell').then((m) => m.AdminShell),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/admin/admin-dashboard/admin-dashboard').then((m) => m.AdminDashboard),
      },
      {
        path: 'procedures',
        loadComponent: () =>
          import('./features/admin/procedures/procedure-admin-list/procedure-admin-list').then(
            (m) => m.ProcedureAdminList,
          ),
      },
      {
        path: 'procedures/:code',
        loadComponent: () =>
          import('./features/admin/procedures/procedure-admin-detail/procedure-admin-detail').then(
            (m) => m.ProcedureAdminDetail,
          ),
      },
      {
        path: 'procedures/:code/versions/:versionNumber',
        loadComponent: () =>
          import('./features/admin/procedures/procedure-version-editor/procedure-version-editor').then(
            (m) => m.ProcedureVersionEditor,
          ),
      },
      {
        path: 'rules',
        loadComponent: () => import('./features/admin/rules/rule-admin-list/rule-admin-list').then((m) => m.RuleAdminList),
      },
      {
        path: 'rules/:code',
        loadComponent: () =>
          import('./features/admin/rules/rule-admin-detail/rule-admin-detail').then((m) => m.RuleAdminDetail),
      },
      {
        path: 'rules/:code/versions/:versionNumber',
        loadComponent: () =>
          import('./features/admin/rules/rule-version-editor/rule-version-editor').then((m) => m.RuleVersionEditor),
      },
      {
        path: 'thresholds',
        loadComponent: () =>
          import('./features/admin/thresholds/threshold-admin-list/threshold-admin-list').then(
            (m) => m.ThresholdAdminList,
          ),
      },
      {
        path: 'thresholds/:code',
        loadComponent: () =>
          import('./features/admin/thresholds/threshold-admin-detail/threshold-admin-detail').then(
            (m) => m.ThresholdAdminDetail,
          ),
      },
      {
        path: 'sources',
        loadComponent: () =>
          import('./features/admin/sources/source-admin-list/source-admin-list').then((m) => m.SourceAdminList),
      },
      {
        path: 'sources/:id',
        loadComponent: () =>
          import('./features/admin/sources/source-admin-detail/source-admin-detail').then((m) => m.SourceAdminDetail),
      },
      {
        path: 'questionnaires',
        loadComponent: () =>
          import('./features/admin/questionnaires/questionnaire-admin-list/questionnaire-admin-list').then(
            (m) => m.QuestionnaireAdminList,
          ),
      },
      {
        path: 'questionnaires/:code',
        loadComponent: () =>
          import('./features/admin/questionnaires/questionnaire-admin-detail/questionnaire-admin-detail').then(
            (m) => m.QuestionnaireAdminDetail,
          ),
      },
      {
        path: 'reviews',
        loadComponent: () => import('./features/admin/reviews/review-queue/review-queue').then((m) => m.ReviewQueue),
      },
      {
        path: 'audit',
        loadComponent: () => import('./features/admin/audit/audit-log/audit-log').then((m) => m.AuditLog),
      },
      {
        path: 'users',
        loadComponent: () => import('./features/admin/users/user-admin/user-admin').then((m) => m.UserAdmin),
      },
    ],
  },
  {
    // Phase 11 brief §192 - draft, honestly-marked legal/policy pages, public and
    // unauthenticated (no reason to gate a disclosure page behind login), and
    // deliberately indexable (unlike the private routes above) since they're the
    // pages a search engine or a curious user should actually be able to find.
    path: 'privacy',
    loadComponent: () => import('./features/legal/privacy-policy/privacy-policy').then((m) => m.PrivacyPolicy),
  },
  {
    path: 'terms',
    loadComponent: () => import('./features/legal/terms-of-service/terms-of-service').then((m) => m.TermsOfService),
  },
  {
    path: 'cookies',
    loadComponent: () => import('./features/legal/cookie-policy/cookie-policy').then((m) => m.CookiePolicy),
  },
  {
    path: 'disclaimer',
    loadComponent: () => import('./features/legal/disclaimer/disclaimer').then((m) => m.Disclaimer),
  },
  {
    path: '**',
    data: { noIndex: true },
    loadComponent: () => import('./features/not-found/not-found').then((m) => m.NotFound),
  },
];
