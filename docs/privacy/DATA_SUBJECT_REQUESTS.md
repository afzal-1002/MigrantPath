# Data Subject Requests - Operational Runbook

Canonical Phase 12. How access/export/deletion/correction actually work in this
application today.

## Access / Export

Fully self-service. `GET /api/v1/account/export` (frontend: Account page's "Export my
data" button) returns a JSON download of everything in `docs/privacy/
DATA_CLASSIFICATION.md`'s PERSONAL rows for the authenticated account, generated
synchronously (current data volume is small - `AccountExportService`). No manual
process needed; no statutory response-time deadline is asserted here (see
`GDPR_READINESS.md` - "legal review required" for any such deadline).

## Deletion

Fully self-service. `POST /api/v1/account/delete` (frontend: Account page's "Delete my
account" dialog) requires current-password reauthentication and an explicit typed
"DELETE" confirmation, then permanently removes the account and its personal-data graph
(`AccountDeletionService`) - see `docs/product/PHASE_12_REPORT.md`'s deletion matrix for
exactly what's removed vs. preserved. Works identically for a plain `USER` account and
for a `CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN` account with real governance history
(V48 migration + `AdminReview.submittedByActorRef`) - no manual staff-offboarding
process is needed for this specific step.

## Correction

- **Account/profile fields** (name, preferred language): self-service via `PATCH
  /api/v1/users/me` (Phase 2, unchanged).
- **Assessment answers**: a user cannot edit a *completed* Assessment's historical
  answers directly - that would silently rewrite what a past Recommendation was based
  on. The correct path is to start a new Assessment (the existing "restart" flow,
  Phase 5), which produces a new, independent, immutable record; the old one remains
  exactly as it was, honestly representing what was true/asked at that time.
  `docs/privacy/DATA_SUBJECT_REQUESTS.md` (this document) and the export/privacy UI
  copy should make this distinction clear to the user, not imply their old answers get
  silently rewritten.
- **Historical legal content** (Procedure/Rule/Threshold text) is never something a
  privacy request can "correct" - it isn't the requester's personal data at all; a
  factual error there goes through `docs/operations/INCIDENT_RESPONSE.md`'s content
  workflow instead.

## Requests this application cannot yet fully self-serve

Anything not covered by the two buttons above (e.g. "export just my assessments, not my
whole account" partial requests, or a request from someone who can no longer log in to
their account) currently has no dedicated tooling and would need manual, ad-hoc
handling by whoever operates the deployment - querying the tables named in
`DATA_INVENTORY.md` for the relevant account id. This is a real, disclosed gap, not
built out this phase (no ticketing/request-tracking system exists in this codebase).

## Response-time commitments

**Not asserted here.** Any specific statutory deadline (e.g. "within one month") is a
legal/business decision, not an engineering one - see `GDPR_READINESS.md`'s "Legal
review required" list. This document describes only what the system can technically do
and how fast the self-service paths actually are (synchronous, immediate).
