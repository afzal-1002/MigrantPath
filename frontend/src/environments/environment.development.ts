// Used by `ng serve` / `npm start`. Relative, same as environment.ts (production) -
// `proxy.conf.json` makes the Angular dev server proxy `/api` and `/actuator` to
// `http://localhost:8080` itself, so the browser only ever talks to
// `localhost:4200`, same-origin, exactly mirroring production's reverse-proxy
// topology (ARCHITECTURE.md §13).
//
// This isn't just tidiness: Angular's built-in XSRF interceptor deliberately skips
// attaching the X-XSRF-TOKEN header on genuinely cross-origin requests (a real
// browser-security feature, not a bug - see its source, `xsrfInterceptorFn`,
// comparing `location.origin` against the request's origin). Pointing this at
// `http://localhost:8080` directly would make every unsafe request fail CSRF
// validation in a real browser even though curl/MockMvc-style tests (which attach
// the header manually) wouldn't show the problem - discovered by actually running
// the Playwright suite in a real browser, not assumed.
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
};
