# Data Flow

Canonical Phase 12. The real, deployed topology (ADR-013), and which personal-data
categories cross which boundary.

```text
Browser (session cookie + XSRF-TOKEN cookie only - no personal data cached client-side
  beyond the current-user summary held in memory, see docs/privacy/LOGGING_PRIVACY.md's
  browser-storage note)
   |
   |  HTTPS (TLS terminated by whatever reverse proxy/load balancer the deployment
   |  target provides - out of this repo's scope, ADR-013)
   v
nginx reverse proxy (frontend/nginx.conf.template)
   |  /api, /actuator -> proxied, same-origin
   |  everything else -> static Angular build
   v
Spring Boot backend
   |
   |--> PostgreSQL 18 (all personal data described in DATA_CLASSIFICATION.md lives here,
   |     encrypted at rest per whatever the managed database provider offers - a
   |     deployment-time choice, ADR-013/DEPLOYMENT.md)
   |
   |--> SMTP provider (verification/reset emails only - carries the recipient's email
   |     address and a one-time token link, never assessment/case content -
   |     docs/operations/EMAIL_PRODUCTION.md)
   |
   |--> Database backups (encrypted, off-instance, restricted access -
   |     docs/operations/DATABASE_BACKUP.md; see this document's own "Backup privacy"
   |     note below for the deletion/backup interaction)
```

## No other personal-data egress exists

No analytics beacon, no advertising pixel, no error-monitoring SDK, no CDN that sees
request bodies (static assets only, per `frontend/nginx.conf.template`'s own cache
rules). If a future phase adds error monitoring, its data flow must be added here
before it ships (`docs/privacy/LOGGING_PRIVACY.md`'s own forward-looking redaction
requirement for that integration).

## Backup privacy (canonical brief item)

A deleted account's personal data is genuinely removed from the live database
immediately (`AccountDeletionService`, a single `DELETE FROM users` with real `ON
DELETE CASCADE` foreign keys - see `docs/product/PHASE_12_REPORT.md`). It is **not**
immediately removed from already-taken database backups - this is a real, disclosed
limitation, not an oversight:

- Backups are not used for ordinary application processing (`docs/operations/
  DATABASE_BACKUP.md`) - a deleted user's data sitting in a backup file is not
  reachable through the running application at all.
- Backups are encrypted, access-restricted, and expire on the retention schedule that
  document states.
- **Restoring an old backup can resurrect data deleted after that backup was taken** -
  `docs/operations/DATABASE_RESTORE.md` is updated with an explicit warning to this
  effect and a reconciliation step (re-apply any deletions known to have happened
  between the backup date and the restore, using the `ACCOUNT_DELETION_COMPLETED`
  `AuditLog` rows - which, being nullable-actor rows referencing only the account's own
  id, survive the account's own deletion - as the source of truth for what to re-delete).
  No separate, always-on deletion ledger was built for this - the existing AuditLog
  already serves this purpose, and building a second, parallel record store was judged
  unnecessary complexity for the documented, low-frequency "restore an old backup"
  scenario (brief's own "avoid overbuilding" guidance).
