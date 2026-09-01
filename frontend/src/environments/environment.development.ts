// Used by `ng serve` / `npm start` (angular.json's default "development"
// configuration). The backend runs on a different origin in local dev
// (localhost:8080 vs localhost:4200), which is exactly the cross-origin case
// SecurityConfig's CORS allowlist (backend `app.cors.allowed-origins`,
// FRONTEND_URL=http://localhost:4200) exists for - see docs/architecture/ARCHITECTURE.md
// §11 / brief §15. Production instead serves same-origin behind a reverse proxy
// (see environment.ts), so this cross-origin setup is local-only.
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
};
