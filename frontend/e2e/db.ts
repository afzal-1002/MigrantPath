import { execFileSync } from 'node:child_process';

// Direct SQL against the developer's/CI's real docker-compose PostgreSQL - only ever
// used to grant a Phase 4 content-management role to a test-created user (brief §44
// deliberately requires an actor with one of these roles; no such actor exists via any
// public API, by design - self-escalation must not be possible). Never used to insert
// fabricated legal content; that stays strictly forbidden (brief §54/§116) - see
// reference-content.spec.ts, which is the only test that needs this at all.
const CONTAINER = 'foreigner-warsaw-postgres-1';
const DB_USER = 'foreigner_warsaw';
const DB_NAME = 'foreigner_warsaw';

export function grantRole(email: string, roleCode: string): void {
  const sql = `INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = '${email}' AND r.code = '${roleCode}'
    ON CONFLICT DO NOTHING;`;
  execFileSync('docker', ['exec', '-i', CONTAINER, 'psql', '-U', DB_USER, '-d', DB_NAME, '-c', sql], {
    stdio: 'pipe',
  });
}
