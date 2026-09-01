// Production build config (angular.json's "production" configuration replaces
// environment.ts with this file - see fileReplacements). apiBaseUrl must be supplied
// at build/deploy time for real environments; this placeholder documents the shape and
// deliberately fails loudly rather than silently pointing at localhost. See
// docs/development/LOCAL_SETUP.md and IMPLEMENTATION_PLAN.md §13.
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
};
