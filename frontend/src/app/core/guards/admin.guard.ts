import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

const ADMIN_ROLES = ['CONTENT_EDITOR', 'LEGAL_REVIEWER', 'ADMIN'];

/**
 * UX-only, exactly like {@link import('./auth.guard').authGuard} (brief §14/§82) - every admin
 * endpoint independently enforces its own role requirement server-side via SecurityConfig; this
 * guard only stops a USER-only account from seeing an admin page flash before its API calls fail
 * with 403. A plain USER (or an unauthenticated visitor) is redirected to `/dashboard` rather than
 * `/login`, since an unauthenticated visitor is already caught by `authGuard` on the parent route.
 */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const roles = authService.currentUser()?.roles ?? [];
  if (roles.some((role) => ADMIN_ROLES.includes(role))) {
    return true;
  }
  return router.parseUrl('/dashboard');
};
