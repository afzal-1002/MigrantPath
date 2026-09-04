# Data Retention Policy

Status: this describes current, actual behavior (mostly: retain indefinitely, no
automated deletion job exists yet) plus the intended policy - the gap between the two is
called out explicitly rather than glossed over. Backs the `/privacy` page's retention
claims.

## Current actual behavior (no automated expiry implemented yet)

| Data | Current behavior | Intended policy | Gap |
|---|---|---|---|
| Account data | Retained for the life of the account; **real, tested, self-service deletion now exists** (`POST /api/v1/account/delete`, canonical Phase 12) - immediate, not a 30-day queued process. | Retained for the life of the account, deleted immediately on a reauthenticated, confirmed deletion request. | None - implemented. Exact wording for a public-facing policy statement still needs legal review (`GDPR_READINESS.md`). |
| Unverified accounts | Retained indefinitely today - no cleanup job removes an account that never completed email verification. | Auto-purge after some proposed window (e.g. 30 days) unverified. | Not implemented this phase either - scoped out in favor of the higher-priority self-service export/deletion work; still a real, tracked follow-up. |
| Password-reset / email-verification tokens | **Real, tested, scheduled cleanup now exists** (`TokenCleanupService`, canonical Phase 12) - removes expired-and-never-used tokens, and used tokens after a configurable retention window (`app.token-cleanup.used-token-retention`, default `P1D`). Off by default in the base profile, on in local/staging/production (`application-*.yml`). | Same as current behavior. | None - implemented and tested (`TokenCleanupServiceTest`). Single-instance scheduler only, see that class's own Javadoc for the horizontal-scaling note. |
| Assessment/recommendation/case data | Retained for the life of the account; **removed immediately and completely on account deletion** (real `ON DELETE CASCADE` foreign keys, verified by `AccountPrivacyIntegrationTest`). | Same as current behavior. | None - implemented. |
| Application/security logs | Retained per whatever the eventual hosting/log-aggregation platform's own default retention is (no platform chosen yet - ADR-013 is provider-neutral). | A bounded retention window (a common default: 30-90 days) once a real log platform is chosen. | Cannot be finalized until a concrete hosting/logging target is chosen - named as a deployment-time decision, not fabricated as already configured. |
| Sessions | **VERIFIED** (not newly built) - `spring.session.store-type: jdbc` with a 30-minute timeout auto-configures Spring Session's own scheduled cleanup of expired JDBC session rows (`JdbcIndexedSessionRepository`'s default cleanup, Spring Boot auto-configuration - no custom code needed or added). | Same. | None - verified, not assumed; see `docs/testing/TEST_COVERAGE_MATRIX.md` for how this was confirmed. |
| Database backups | Per `docs/operations/DATABASE_BACKUP.md`'s stated policy (daily, encrypted, off-instance) - see that document for the actual retention window (30 days rolling, documented there, not duplicated here to avoid drift between two "sources of truth"). | Same. | None - this one is real and documented. |
| Audit log (admin content-governance actions) | Retained indefinitely - this is intentionally permanent, not subject to user-deletion requests, since it records *admin* actions on *legal content*, not end-user personal data (ADR-004's accountability requirement). | Same - permanent. | None - this is a deliberate design choice, not a gap. |

## Principle going forward

Any new feature that stores personal data must state its retention period in this
document before (or at the same time as) it ships - this document should never be
allowed to silently drift out of date the way a stale README would (the same discipline
already applied to `docs/legal-content/PRODUCTION_RULE_COVERAGE.md` for rule coverage).
