# Data Inventory

Status: an honest inventory of what this application actually stores, derived from the
real schema (docs/database/DATABASE.md) and entities, not a generic privacy-policy
template. Written to back the real `/privacy` page's claims - every category listed
there must trace to a row here.

This is **not** attorney-reviewed (see docs/privacy's own disclaimer and the `/privacy`
page itself) - it is an accurate engineering-side inventory, a necessary input to a real
legal review, not a substitute for one.

## Categories of personal data collected

| Category | Fields | Table(s) | Why collected |
|---|---|---|---|
| Account/identity | email, password hash, first/last name, roles | `users`, `roles`, `user_roles` | Authentication, authorization, addressing the user by name in the UI/email. |
| Account lifecycle | email-verification token hash, expiry; password-reset token hash, expiry; lockout counters | `users` (Phase 2 columns) | Prove account ownership; prevent brute force. Only a hash of any token is ever stored (CLAUDE.md standing rule). |
| Assessment answers | per-question answers (nationality/country group, purpose of stay, income, family situation, etc. - see docs/product/ASSESSMENT_DECISION_TREE.md) | `assessments`, `assessment_answers` | The guided questionnaire the entire product is built around - drives rule evaluation. Full purpose-by-question audit: `docs/privacy/DATA_PURPOSES.md`. |
| Recommendation history | which procedures were recommended, recommendation type, reasons, timestamp | `recommendation_runs`, `recommendations` | Lets the user see why a procedure was/wasn't recommended; supports the "explain, never decide" AI boundary (ADR-003) even though no AI is wired to this data yet. |
| Case/checklist progress | which procedure the user is pursuing, per-step/document/fee checklist state, case status, user notes, case events, snapshot revision of the procedure/rule version at creation | `user_cases`, `user_case_snapshot_revisions`, `user_case_steps`, `user_case_documents`, `user_case_fees`, `user_case_events` | The personalized checklist feature (Phase 8) - the core "am I done yet" tracking. |
| Consent records | which policy was accepted, version, timestamp | `user_consents` | Provable acknowledgement of Terms/Privacy Policy version at registration (Phase 2, real and working since before this phase). |
| Technical/security logs | IP address, user agent, timestamp, correlation id (request-scoped, not persisted to DB), authentication success/failure events | Application logs only (not a DB table) | Security monitoring, abuse detection, debugging. Never includes password/token/answer content (see docs/operations/OBSERVABILITY.md's log-scrub discipline). |
| Admin/audit trail | who published/approved/edited a content version and when | `*_version` tables' own audit columns, `audit_log` (Phase 9) | Content-governance accountability (ADR-004) - applies to admin/content-editor accounts acting on legal content, not to end-user personal data. |

## What this application deliberately does NOT collect

- No payment/billing data (no payments feature exists - explicitly out of scope).
- No government ID numbers, PESEL number, passport number, or immigration case/file
  numbers - the assessment asks about *situation* (nationality group, purpose, income
  band, family status), never the user's actual official identifiers. If a future phase
  needs one (e.g. tracking a real PESEL application inside a case), it must be added
  here explicitly, not silently.
- No document uploads (explicitly out of Phase 11 and current-roadmap scope).
- No location/GPS tracking.
- No third-party analytics/advertising identifiers (brief §108 - "no analytics added"
  this phase).
- No sensitive-category inference beyond what the user explicitly answers in the
  assessment (no automated profiling beyond the deterministic, disclosed rules engine
  itself - ADR-003).

## Who can see what

- The user's own account/assessment/case data: only that user (server-side ownership
  checks on every query, re-verified in `docs/security/PRODUCTION_SECURITY.md`'s IDOR
  row) and, for legal-content governance purposes only (never end-user personal data),
  admin/content-editor/legal-reviewer roles - which do not have any UI or endpoint that
  lists or browses other users' assessments/cases (confirmed: `AdminUserController`-
  style endpoints, where they exist, operate on account/role management only, not on
  assessment or case content).
- Application logs: whoever has infrastructure/hosting access - subject to whatever
  hosting provider is chosen (ADR-013 leaves this provider-neutral).
- Database backups: encrypted, off-instance, access restricted per
  `docs/operations/DATABASE_BACKUP.md`.

## Data subject rights (canonical Phase 12 - now real and self-service)

`GET /api/v1/account/export` and `POST /api/v1/account/delete` (frontend: the Account
page's Export/Delete actions) are real, tested, self-service implementations -
`AccountExportService`/`AccountDeletionService`. See `docs/privacy/
DATA_SUBJECT_REQUESTS.md` for the full runbook (what's self-service vs. still manual)
and `docs/product/PHASE_12_REPORT.md` for the deletion matrix (what's deleted,
anonymized, or retained per entity) and the governance-safe design that lets a
CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN account delete itself without breaking published
legal content or its own review/audit history (V48 migration).
