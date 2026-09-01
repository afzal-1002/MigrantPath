import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * UX-only, per brief §28/§17 - the backend is authoritative regardless of what this
 * guard decides (every protected endpoint still requires a real session server-side).
 * This exists purely so an unauthenticated user is redirected to /login instead of
 * seeing a protected page flash before its data requests fail with 401.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }
  return router.parseUrl('/login');
};
