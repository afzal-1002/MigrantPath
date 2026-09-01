import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { credentialsInterceptor } from './core/interceptors/credentials.interceptor';
import { AuthService } from './core/services/auth.service';

// No animations provider: `@angular/animations` (and provideAnimationsAsync, which
// depends on it) is deprecated as of Angular 22 in favor of native `animate.enter` /
// `animate.leave` template bindings - see
// https://v22.angular.dev/guide/animations. Angular Material components render
// correctly without it; Phase 1 keeps things simple (brief §11) and picks up native
// animations later only where a concrete UI need justifies them.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([credentialsInterceptor]),
      // Matches Spring Security's CookieCsrfTokenRepository defaults exactly
      // (ADR-005) - stated explicitly rather than relied on as an implicit default.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    // Resolves AuthService's UNKNOWN -> AUTHENTICATED/UNAUTHENTICATED state before the
    // app renders (brief §26) - without this, authGuard/guestGuard would see UNKNOWN
    // (falsy isAuthenticated()) on a hard reload and briefly bounce a logged-in user
    // toward /login before the real state resolved.
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      return firstValueFrom(authService.loadCurrentUser());
    }),
  ],
};
