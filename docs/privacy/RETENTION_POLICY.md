# Data Retention Policy

Status: this describes current, actual behavior (mostly: retain indefinitely, no
automated deletion job exists yet) plus the intended policy - the gap between the two is
called out explicitly rather than glossed over. Backs the `/privacy` page's retention
claims.

## Current actual behavior (no automated expiry implemented yet)

| Data | Current behavior | Intended policy | Gap |
|---|---|---|---|
| Account data | Retained until the account is manually deleted (no self-service deletion exists - see `docs/privacy/DATA_INVENTORY.md`'s "Data subject rights" gap). | Retained for the life of the account, deleted (or anonymized, if referenced by retained case/audit history) within 30 days of a verified deletion request. | No automated or self-service deletion pipeline exists. |
| Unverified accounts | Retained indefinitely today - no cleanup job removes an account that never completed email verification. | Auto-purge after 30 days unverified. | Not implemented - a real, scoped follow-up (a scheduled `@Scheduled` job or a Flyway-adjacent cleanup query), named here rather than silently assumed done. |
| Password-reset / email-verification tokens | Already time-limited and single-use (`AuthProperties` TTLs, Phase 2) - expired rows are never re-usable, but expired rows are not yet actively deleted from the table, only ignored by query logic. | Periodic deletion of expired token rows (housekeeping, not a security requirement, since expired tokens are already inert). | No cleanup job exists; low priority since expired tokens are already unusable, but listed as a real "session/token cleanup" item from the brief (§111) rather than silently skipped. |
| Assessment/recommendation/case data | Retained indefinitely today, tied to the owning account. | Same as account data - retained for the life of the account; deleted/anonymized alongside it on a verified deletion request. | Same gap as account deletion above - no self-service or automated pipeline. |
| Application/security logs | Retained per whatever the eventual hosting/log-aggregation platform's own default retention is (no platform chosen yet - ADR-013 is provider-neutral). | A bounded retention window (a common default: 30-90 days) once a real log platform is chosen. | Cannot be finalized until a concrete hosting/logging target is chosen - named as a deployment-time decision, not fabricated as already configured. |
| Database backups | Per `docs/operations/DATABASE_BACKUP.md`'s stated policy (daily, encrypted, off-instance) - see that document for the actual retention window (30 days rolling, documented there, not duplicated here to avoid drift between two "sources of truth"). | Same. | None - this one is real and documented. |
| Audit log (admin content-governance actions) | Retained indefinitely - this is intentionally permanent, not subject to user-deletion requests, since it records *admin* actions on *legal content*, not end-user personal data (ADR-004's accountability requirement). | Same - permanent. | None - this is a deliberate design choice, not a gap. |

## Principle going forward

Any new feature that stores personal data must state its retention period in this
document before (or at the same time as) it ships - this document should never be
allowed to silently drift out of date the way a stale README would (the same discipline
already applied to `docs/legal-content/PRODUCTION_RULE_COVERAGE.md` for rule coverage).
