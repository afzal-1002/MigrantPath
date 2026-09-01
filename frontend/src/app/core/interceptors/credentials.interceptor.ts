import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Centralized so every request carries cookies, rather than setting `withCredentials`
 * on each call site (brief §27) - required for both the session cookie and the
 * XSRF-TOKEN cookie to travel on the cross-origin requests local development makes
 * (localhost:4200 -> localhost:8080). In production, frontend and backend are
 * same-origin behind a reverse proxy (ARCHITECTURE.md §13), where this is a no-op.
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req.clone({ withCredentials: true }));
};
