import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
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

/**
 * Post-MVP UX pass - a `canMatch` sibling to {@link authGuard} for the one route
 * (Procedures) that legitimately exists twice: once under the public `Shell` (for an
 * anonymous visitor) and once under the authenticated `AppShell` (so a logged-in user
 * stays inside their own rail/topbar instead of being dropped back into the public
 * layout - see app.routes.ts's own comment on the duplication). `canMatch` (unlike
 * `canActivate`) failing here doesn't block navigation - it just tells the router "this
 * candidate route doesn't match, try the next sibling config", which is exactly what
 * lets the anonymous version of `/procedures` take over when this one declines.
 */
export const authMatchGuard: CanMatchFn = () => inject(AuthService).isAuthenticated();
