import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Keeps an already-authenticated user from landing back on /login or /register
 * (brief §28) - sends them to the dashboard instead. Also UX-only, same caveat as
 * {@link authGuard}. */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return router.parseUrl('/dashboard');
  }
  return true;
};

/**
 * `canMatch` counterpart to {@link authMatchGuard} in auth.guard.ts - the public
 * `Shell`'s own `/procedures` route declines to match (falls through, doesn't redirect)
 * once a user is authenticated, so the router keeps trying sibling route configs and
 * lands on the `AppShell`-wrapped version instead. See app.routes.ts's comment on the
 * duplicated Procedures route for the full rationale.
 */
export const guestMatchGuard: CanMatchFn = () => !inject(AuthService).isAuthenticated();
