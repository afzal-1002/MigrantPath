import { Routes } from '@angular/router';

// Two routes only for Phase 1 (docs/architecture/ARCHITECTURE.md §10) - real feature
// routes (auth, assessment, procedures, cases, admin, ...) are lazy-loaded as each
// feature module lands, not stubbed out ahead of time.
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: '**',
    loadComponent: () => import('./features/not-found/not-found').then((m) => m.NotFound),
  },
];
