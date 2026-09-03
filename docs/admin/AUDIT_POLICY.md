# Admin Audit Policy

## What is audited

Every administrative content mutation reachable through `/api/v1/admin/**`: procedure/rule/
threshold identity creation, draft version creation/update, step/document/fee add/update/
remove, source creation/verification, submit/approve/request-changes/reject/publish/archive,
and role assignment/removal. Each row (`AuditLog`) records: actor, action type (a specific
domain verb - `CONTENT_PUBLISHED`, `SOURCE_VERIFIED`, `ROLE_ASSIGNED`, never a generic
`UPDATE`), entity type, entity id, the entity's business code (e.g. a Procedure code) where
applicable, the specific version id where applicable, a timestamp, and a short human-readable
summary.

## What is excluded

- Ordinary user-facing activity (assessments, recommendations, cases) - out of scope for this
  audit trail; that is user-facing case history (`UserCaseEvent`, Phase 8), a separate concept.
- Read/list/detail requests - only mutations are recorded.
- Login/logout/password-change security events - those already have their own, separate
  `SecurityEventLogger` (Phase 2), deliberately not merged into this table.
- Content created through the pre-existing Phase 4 `/api/v1/internal/content/**` endpoints
  (procedure/version/step/document/source creation, and the `/sources/{id}/verify` action at
  that path) - those predate `AuditService` and are not retrofitted to write to it. Everything
  reachable only through the new `/api/v1/admin/**` surface (review-workflow transitions,
  role management, and all Rule/Threshold/Questionnaire/Source admin actions) is fully audited.
  A known, documented gap - see PHASE_9_REPORT.md.

## Privacy

Never stores: password hashes, session identifiers, tokens, or any user's Assessment answer.
`metadata` (when present) is small, structured JSON - never a dump of an entity graph.

## Immutability

Audit rows are append-only. No admin UI or API ever edits or deletes one. A failed mutation
(e.g. a rejected publish) never produces a misleading "successful" audit row - the audit write
happens in the same transaction as the mutation it describes, so a rolled-back mutation leaves
no audit trace at all.

## Retention

No automated retention/purge policy exists yet - rows accumulate indefinitely. A future phase
should define one before this becomes an operational concern at scale.

## Access

Reading the audit log (`GET /api/v1/admin/audit`) is `ADMIN`-only, paginated (max 200 rows per
page), and filterable by actor, action type, entity type, entity business code, and a date
range - never an arbitrary SQL-like query surface.
