// Production build config (angular.json's "production" configuration replaces
// environment.ts with this file - see fileReplacements). apiBaseUrl must be supplied
// at build/deploy time for real environments; this placeholder documents the shape and
// deliberately fails loudly rather than silently pointing at localhost. See
// docs/development/LOCAL_SETUP.md and IMPLEMENTATION_PLAN.md §13.
//
// debugQuickLoginEnabled: TEMPORARY, explicitly requested by the user for their own
// manual testing ("just for debugging purposes, I will ask later to remove them") -
// renders one-click test-account buttons on the login page with real credentials
// embedded in the shipped JS bundle. No real production deployment exists yet
// (FINAL_GO_NO_GO.md - no host/domain/TLS cert provisioned), which is the only reason
// this is acceptable to leave true in the "production" build config at all - it MUST
// become false (or be deleted outright, along with login.ts's QUICK_LOGIN_ACCOUNTS)
// before any real public launch. The login component also gates this behind a
// `window.location.hostname` localhost/127.0.0.1 check as a second, independent
// safety net, so this flag alone accidentally staying true is not sufficient to
// expose it on a real deployed domain.
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  debugQuickLoginEnabled: true,
};
