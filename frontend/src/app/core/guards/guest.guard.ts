import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
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
