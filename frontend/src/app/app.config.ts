import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

// No animations provider: `@angular/animations` (and provideAnimationsAsync, which
// depends on it) is deprecated as of Angular 22 in favor of native `animate.enter` /
// `animate.leave` template bindings - see
// https://v22.angular.dev/guide/animations. Angular Material components render
// correctly without it; Phase 1 keeps things simple (brief §11) and picks up native
// animations later only where a concrete UI need justifies them.
export const appConfig: ApplicationConfig = {
  providers: [provideBrowserGlobalErrorListeners(), provideRouter(routes), provideHttpClient()],
};
