import { execFileSync } from 'node:child_process';

// Direct SQL against a real docker-compose PostgreSQL - only ever used to grant a
// Phase 4 content-management role to a test-created user (brief §44 deliberately
// requires an actor with one of these roles; no such actor exists via any public API,
// by design - self-escalation must not be possible). Never used to insert fabricated
// legal content; that stays strictly forbidden (brief §54/§116) - see
// reference-content.spec.ts, which is the only test that needs this at all.
//
// Canonical Phase 13 (Deployment) brief §59: env-overridable, defaulting to the
// developer's/CI's own docker-compose dev stack (docker-compose.yml) - lets this same
// helper also target a local production-like rig (infra/docker-compose.prod.yml under
// a different Compose project name, e.g. `docker exec fw-prod-test-postgres-1`) for a
// one-off local verification run. This does NOT make the full suite staging-capable in
// general: a real remote staging host has no `docker exec` access from a CI runner at
// all (docs/operations/STAGING.md's own disclosed gap) - BASE_URL alone is not enough
// for the admin/content-governance specs that rely on this file; only the specs that
// never call grantRole (assessment/reference-content's public-read half, account
// privacy, etc.) are genuinely BASE_URL-portable to a real remote target today.
const CONTAINER = process.env['E2E_DB_CONTAINER'] ?? 'foreigner-warsaw-postgres-1';
const DB_USER = process.env['E2E_DB_USER'] ?? 'foreigner_warsaw';
const DB_NAME = process.env['E2E_DB_NAME'] ?? 'foreigner_warsaw';

export function grantRole(email: string, roleCode: string): void {
  const sql = `INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = '${email}' AND r.code = '${roleCode}'
    ON CONFLICT DO NOTHING;`;
  execFileSync('docker', ['exec', '-i', CONTAINER, 'psql', '-U', DB_USER, '-d', DB_NAME, '-c', sql], {
    stdio: 'pipe',
  });
}
