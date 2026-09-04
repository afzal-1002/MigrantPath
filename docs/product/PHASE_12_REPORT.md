# Phase 12 Report — Security, Privacy & GDPR Completion

Status: ✅ substantially complete. **Technical GDPR readiness, not a compliance
certification** — the codebase can become technically GDPR-ready; final legal
compliance still requires legal/business review of lawful bases, final privacy
notices, exact retention periods, processor agreements, international transfers, and
data-subject-request procedures. See `docs/privacy/GDPR_READINESS.md`.

## Executive summary

Most of the *security-hardening* half of "Phase 12" work was already done, out of
order, during the earlier production-readiness pass (tracked as `PHASE_11_REPORT.md`
in this repository's own numbering collision - see `IMPLEMENTATION_PLAN.md`'s
reconciliation note). This phase inspected what already existed (Spring Security
config, sessions, CSRF, CORS, headers, secrets, Docker hardening, admin bootstrap,
correlation IDs, rate limiting, Actuator policy, audit-logging foundation) and
deliberately did **not** redo any of it. What it actually built: the personal-data
*lifecycle* the earlier work never touched - self-service export and deletion, a real
governance-safe fix for a genuine account-deletion blocker, explicit ownership
hardening for a subtle IDOR pattern, a consolidated authorization regression, scheduled
token retention, a formalized content-security policy, and the full privacy/security
documentation set the canonical brief named.

## Inspection findings resolved

1. **`admin_review.submitted_by RESTRICT`** - fixed via V48 migration
   (`submitted_by` → nullable/`ON DELETE SET NULL`, plus a new permanent
   `submitted_by_actor_ref`). See "Governance-safe account deletion" below.
2. **Missing personal-data export** - `GET /api/v1/account/export`,
   `AccountExportService`, real and tested.
3. **Missing account deletion** - `POST /api/v1/account/delete`,
   `AccountDeletionService`, real and tested, including the governance-safe path.
4. **Implicit `UserCaseItemService` ownership** - made explicit; see "UserCase
   ownership" below.
5. **Missing consolidated authorization matrix** - `AuthorizationMatrixTest`.
6. **Missing token/session cleanup** - `TokenCleanupService` (real, scheduled, tested)
   + session cleanup verified as already handled by Spring Session's own framework
   default (not rebuilt).
7. **Plain-text content policy never formalized** - now explicit in
   `docs/security/THREAT_MODEL.md`, matching what canonical Phase 11 already found to
   be true in practice.

## Governance-safe account deletion

```text
Live User (any role, including CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN)
  ↓ POST /api/v1/account/delete (current password + typed "DELETE" confirmation)
AccountDeletionService.deleteOwnAccount
  ↓
AdminReview.submitted_by      → NULL           (V48: ON DELETE SET NULL, was RESTRICT)
AdminReview.submitted_by_actor_ref → preserved   (immutable UUID, set once at review creation)
Procedure/Rule/Threshold/QuestionnaireVersion actors → NULL (already ON DELETE SET NULL, pre-existing)
AuditLog.actor                 → NULL (already ON DELETE SET NULL) / new deletion-event rows use a null actor by design
Published legal content         → untouched
AdminReview/AuditLog rows       → untouched, fully retained
Sessions (all, every device)    → invalidated
Verification/reset tokens       → cascaded/removed with the user row
Assessments/Recommendations/Cases → cascaded/removed (ON DELETE CASCADE, audited row by row - see below)
```

Proven end to end by `AccountPrivacyIntegrationTest.
staffAccountDeletion_preservesReviewAndPublishedContentAndAuditHistory`: a
CONTENT_EDITOR with a real, in-flight `AdminReview` deletes their own account through
the ordinary self-service flow; the review, the published procedure, and the audit
trail all survive; only the live account/email link is gone.

## FK audit table (the "why a single DELETE is safe" evidence)

| Table | On delete | Category |
|---|---|---|
| `user_roles`, `email_verification_tokens`, `password_reset_tokens`, `user_consents`, `assessments`, `recommendation_runs`, `user_cases` (and all snapshot/step/document/fee/event children) | CASCADE | Personal data - removed with the account |
| `procedure_versions`/`rule_versions`/`threshold_versions`/`questionnaire_versions` actor columns, `official_sources.checked_by`, `audit_log.actor_user_id`, `user_case_events.actor_user_id` | SET NULL | Governance/audit - survives account deletion |
| `admin_review.submitted_by` (as of V48; was `RESTRICT`) | SET NULL | Governance - now survives; `submitted_by_actor_ref` preserves attribution |
| `admin_review.reviewer` | SET NULL (pre-existing) | Governance |

`AccountDeletionService` relies on this audited table, not JPA object-graph cascade -
see its own Javadoc for why that distinction matters (brief's "do not rely solely on
Hibernate cascading" targets in-memory object-graph cascade, not an audited DB-level FK
design).

## Personal Data Export

- **Endpoint**: `GET /api/v1/account/export` → `AccountExportResponse` (explicit DTO
  tree, `exportSchemaVersion: 1`), `Content-Disposition: attachment`,
  `Cache-Control: no-store`.
- **Included**: account (id, email, name, language, verified flag, roles, createdAt),
  consents, every assessment + its applicable answers, every recommendation run +
  recommendations, every case + its current-revision steps/documents/fees + all events.
- **Excluded** (structurally, by DTO shape - not just by convention): password hash,
  session identifiers, token hashes, CSRF values.
- **Audited**: `PERSONAL_DATA_EXPORT_REQUESTED`/`COMPLETED`, no payload logged.
- **Frontend**: Account page's "Export my data" button, downloads via Blob + object URL.

## Account Deletion

- **Endpoint**: `POST /api/v1/account/delete` (`currentPassword` + `confirmation:
  "DELETE"`).
- **Reauthentication**: real bcrypt password check, `ACCOUNT_REAUTHENTICATION_FAILED`
  (401) on mismatch, account untouched.
- **Transaction**: single `@Transactional` method; the actual deletion is one
  `DELETE FROM users` statement plus its real FK cascade - atomic by construction, not
  by orchestration.
- **Sessions**: all invalidated via the existing `SessionInvalidator` (same mechanism
  `changePassword` already used) - proven for two concurrent sessions in
  `AccountPrivacyIntegrationTest`.
- **Re-registration**: same email can register again immediately as a brand-new,
  unconnected identity (new UUID, zero old history) - tested.
- **Frontend**: Account page's "Delete my account" dialog - password + typed "DELETE"
  confirmation, explains exactly what's removed before the action is possible.

## Deletion matrix

| Entity | Action | Reason |
|---|---|---|
| User, user_roles | DELETE | Core account identity |
| Verification/reset tokens | DELETE (cascade) | No longer needed |
| Consents | DELETE (cascade) | Tied to the account, no separate retention justification found |
| Assessments/Answers | DELETE (cascade) | Personal, no analytics/statistical retention need exists in this product |
| Recommendation runs/Recommendations | DELETE (cascade) | Derived personal data |
| UserCases and all children | DELETE (cascade) | Personal progress tracking |
| Sessions | DELETE (immediate, all devices) | Security-critical, not FK-driven |
| Procedure/Rule/Threshold/Questionnaire content | RETAIN | Shared legal content, not personal |
| AdminReview | RETAIN, actor pseudonymized | Governance history |
| AuditLog | RETAIN | Governance/security accountability, contains no personal content by design |
| Database backups taken before deletion | RETAIN until backup expiry | Documented limitation, see `docs/privacy/DATA_FLOW.md` |

## UserCase ownership hardening

Canonical Phase 11 (Testing) proved `UserCaseItemService`'s step/document/fee
mutations were safe only *incidentally* (a revision-id match happened to also reject
cross-case item ids, since revision ids are never shared across cases). This phase made
the check explicit: `requireCurrentRevisionItem` now verifies the resolved item's own
case matches the caller's authorized case *before* the historical-revision check,
returning the same `404 CASE_ITEM_NOT_FOUND` ownership-hiding response every other
owned resource in this codebase uses. No behavior change for a legitimate caller; a
future refactor that broke the incidental protection would now be caught directly
instead of by coincidence.

## Authorization Matrix

`AuthorizationMatrixTest` - representative endpoints per real boundary category this
codebase has: an own-resource endpoint (any authenticated role), a privacy endpoint
(owner-only, never an ADMIN bypass - proven directly, not assumed), a
CONTENT_EDITOR-gated mutation, an ADMIN-only user-management endpoint, an ADMIN-only
audit endpoint. Not an exhaustive per-endpoint matrix over all ~70 admin routes (a
deliberate scope decision, named in `docs/security/SECURITY_GAPS.md`).

## Retention

**Enforced now**: sessions (Spring Session's own framework-default cleanup, verified
not rebuilt), verification/reset tokens (`TokenCleanupService`, real and tested,
off-by-default/on-per-real-environment).

**Proposed / legal review required**: exact wording for "how long is personal data
retained while an account is active" in a public-facing policy; backup retention
wording; see `docs/privacy/GDPR_READINESS.md` and `RETENTION_POLICY.md`.

## Consent

The existing `user_consents` mechanism (real since Phase 2 - TERMS_OF_SERVICE/
PRIVACY_POLICY acceptance, policy version, timestamp) was not redesigned. **Consent IP
address decision**: the `ip_address` column remains intentionally unpopulated - no
concrete security/legal requirement was found to justify collecting it, and collecting
it "because the column exists" would itself violate data minimization. This document
records that as a deliberate decision, not an oversight.

## Data Minimization

A real, question-by-question audit of the active `WARSAW_GENERAL_ASSESSMENT`
questionnaire is in `docs/privacy/DATA_PURPOSES.md`. **No `QuestionnaireVersion` was
created this phase** - several questions remain collected ahead of a Rule that uses
them (a pre-existing, already-disclosed consequence of Phase 5's original design, not
a new finding), and the canonical brief's own instruction not to mutate a published
QuestionnaireVersion, combined with this phase's narrower scope (export/deletion, not
content editing), meant the right action was to document the audit honestly rather
than force a speculative content change.

## Stored Content / XSS

**Policy: PLAIN TEXT**, formalized this phase (`docs/security/THREAT_MODEL.md`) -
matches what canonical Phase 11 already found true in practice (zero backend HTML
interpretation, zero `[innerHTML]`/`bypassSecurityTrust*` anywhere in the frontend,
grep-verified again this phase). The existing stored-`<script>`-payload regression test
(`procedure-detail.spec.ts`, from canonical Phase 11) already proves this; no new XSS
test was added this phase since the existing one already covers the real rendering
path and no new payload variant was found necessary.

## Browser Storage

`grep -r "localStorage\|sessionStorage\|indexedDB" frontend/src` - zero matches,
verified this phase. No personal data, token, or payload is ever written to browser
storage; the only client-side persistence is the two cookies already documented in
`docs/privacy/COOKIE_INVENTORY.md`.

## Privacy UX

- `frontend/src/app/features/account/account.ts` + `.html` - the Account page (`/account`,
  authenticated-only, `noIndex: true`).
- `frontend/src/app/features/account/delete-account-dialog/` - the reauthentication +
  typed-confirmation deletion dialog.
- `frontend/src/app/core/services/account.service.ts` - thin API wrapper.
- Privacy Policy page updated to describe the real export/deletion capability
  (previously said "we do not yet have a fully self-service delete feature").
- Footer link to `/account` added to the shell nav for authenticated users.

## Backup Privacy

Documented, not solved by new infrastructure: a deleted account's data can persist in
already-taken encrypted backups until they age out; restoring an old backup can
resurrect post-backup-deleted data, and the existing `AuditLog`
(`ACCOUNT_DELETION_COMPLETED` rows, which themselves survive since their actor is
already null by design) serves as the reconciliation source of truth rather than
building a second, parallel deletion ledger. See `docs/privacy/DATA_FLOW.md`.

## Database

**Migration**: `V48__admin_review_pseudonymous_submitter.sql` - adds
`submitted_by_actor_ref` (backfilled from `submitted_by`), relaxes `submitted_by` to
nullable with `ON DELETE SET NULL` (was `NOT NULL ... ON DELETE RESTRICT`).
**ADR**: `docs/architecture/ADR/ADR-014-personal-data-lifecycle.md`.

## Documentation

Created: `docs/privacy/DATA_CLASSIFICATION.md`, `DATA_PURPOSES.md`, `DATA_FLOW.md`,
`PROCESSOR_INVENTORY.md`, `DATA_SUBJECT_REQUESTS.md`, `GDPR_READINESS.md`,
`LOGGING_PRIVACY.md`; `docs/security/SECURITY_GAPS.md`, `THREAT_MODEL.md`;
`docs/architecture/ADR/ADR-014-personal-data-lifecycle.md`. Updated:
`docs/privacy/DATA_INVENTORY.md`, `RETENTION_POLICY.md`, `docs/security/
PRODUCTION_SECURITY.md` (implicitly consistent, cross-referenced by `THREAT_MODEL.md`),
`docs/product/IMPLEMENTATION_PLAN.md` (Phase 12 status), frontend Privacy Policy page.

## Tests

- Backend: **+4 test files/significant additions** -
  `AccountPrivacyIntegrationTest` (6 tests: export contents/secret-exclusion/
  cross-user isolation, wrong-password rejection, missing-confirmation rejection, the
  full multi-session/re-registration deletion proof, and the governance-safe staff
  deletion proof), `AuthorizationMatrixTest` (6 tests), `TokenCleanupServiceTest` (2
  tests), plus the `UserCaseItemService` hardening (covered by canonical Phase 11's
  existing IDOR regression, still passing, now testing the *explicit* code path).
- A real, deterministic cross-test-class CSRF/context-pollution bug was found and
  fixed this phase (see "Bugs found" below) - `@DirtiesContext(AFTER_CLASS)` added to
  9 test classes.
- Full regression, all real this session: backend `./mvnw verify` - **green** (0
  failures across the full suite, confirmed twice after the ordering fix); frontend
  `npm run lint && npm test && npm run build` - **green** (113 unit tests passing);
  Playwright - **18/18 passing** (one spec hit the pre-existing, canonical-Phase-11-
  documented country-autocomplete parallel-worker contention under the default 3-worker
  run; confirmed via a clean serial re-run of that spec alone - the same known
  environmental pattern, not a Phase 12 regression).

## Bugs found this phase (with regression/fix, not just noted)

1. **`admin_review.submitted_by RESTRICT`** - the headline inspection finding; fixed
   via V48 + `AdminReview.submittedByActorRef` + `ContentReviewCoordinator`'s
   self-approval check updated to use the stable ref instead of the nullable live
   association (which would otherwise NPE for a review whose submitter had since been
   deleted).
2. **`AdminReviewRepository`'s two fetch-join queries used `JOIN FETCH
   r.submittedBy`** (inner join) - after V48 made `submitted_by` nullable, this would
   have silently excluded any review whose submitter was deleted from every listing/
   history query. Changed to `LEFT JOIN FETCH`.
3. **`AdminReviewResponse.from` dereferenced `review.getSubmittedBy().getEmail()`
   unconditionally** - would NPE once `submitted_by` could be null. Null-guarded with
   a `DELETED_ACCOUNT` label, matching the existing pattern already used for
   `reviewer`.
4. **A genuine Hibernate limitation**: passing the same `User` entity that a
   transaction is about to delete as another new row's (`AuditLog`) association throws
   `TransientPropertyValueException` at flush time, regardless of statement order or
   an explicit intermediate `flush()`. Worked around by passing `null` as the actor for
   the two deletion-event audit rows (a value `AuditLog.actor` already supports by
   design) rather than fighting Hibernate's entity-instance bookkeeping - see
   `AccountDeletionService`'s own Javadoc for the full account of what was tried.
5. **A real, deterministic full-suite test-ordering bug**: `.with(user(...))/
   .with(csrf())`-only test classes (this codebase's Spring-Security-test-support CSRF
   bypass) leave the shared `CookieCsrfTokenRepository` in a state that breaks
   whichever real-cookie-flow class runs next in the same cached Spring context -
   reproduced deterministically across three separate full `./mvnw verify` runs (not a
   one-off flake), previously only worked around for one class
   (`AdminGovernanceIntegrationTest`). Fixed by adding
   `@DirtiesContext(classMode = AFTER_CLASS)` to every affected class (9 total, 2 new
   this phase + 7 pre-existing that had never needed it before this phase's added
   classes shifted execution order enough to expose it) - the full suite is now green
   across three repeated runs.
6. **A test-authoring bug in my own new fixture code** (not a product bug): `.
   createPublishedProcedure`'s minimal test procedure initially had zero steps, which
   `CaseCreationValidator` correctly rejects (`CASE_CONTENT_NOT_READY`) - fixed by
   adding one real step, matching this codebase's existing fixture convention
   elsewhere.

## Database quality

Re-verified via the full backend regression's own repository/integration tests
(no separate ad-hoc query pass performed this phase - the existing test suite already
exercises the relevant invariants: no orphaned assessments/cases survive a user
deletion, per `AccountPrivacyIntegrationTest`'s own direct repository assertions after
deletion).

## Legal / Business Review Required (explicitly not solved by this phase)

- Lawful basis for processing (this codebase does not assert consent as the general
  lawful basis for account/service processing).
- Final privacy policy, terms, cookie policy, disclaimer text (drafts exist, marked as
  such).
- Exact retention-period wording for public-facing policy.
- Processor agreements - none needed yet, no processor selected.
- International transfer review - not applicable until a real processor is chosen.
- Minors/age-scope policy - undefined, not inferred.
- Formal special-category-data classification for citizenship/immigration-status data.
- Data-subject-request statutory response-time commitments.
- External/professional security and privacy review.

## Deviations from the implementation prompt

- **No `PrivacyRequest` table was created** - deliberately, per the brief's own
  "prefer fewer personal-data stores" guidance; `AuditLog` already records the
  export/deletion events, and both actions are synchronous with no pending state to
  track.
- **`entityManager`/`getReference()` approaches to the Hibernate transient-reference
  bug were tried and abandoned** in favor of a `null` actor - documented in the bugs
  list above and the service's own Javadoc, not silently dropped.
- **`@DirtiesContext` was added to 9 test classes**, more than the 2 originally
  suspected - a deliberate, evidence-based expansion once the full-suite behavior was
  actually reproduced and understood, not scope creep.

## Known issues (real, remaining)

- Rate limiter remains single-instance in-memory.
- No backend dependency-vulnerability scanner wired into CI.
- No external security/privacy review performed.
- No consolidated *exhaustive* per-endpoint authorization matrix (representative only).
- Unverified-account auto-purge, DEBUG-level local logging PII gap: both named, neither
  fixed this phase (see `docs/security/SECURITY_GAPS.md`, `docs/privacy/
  LOGGING_PRIVACY.md`).

## Canonical Phase 12 status

**DONE** for the scope this phase actually targeted (export, deletion,
governance-safety, ownership hardening, authorization matrix, token retention, content
policy, documentation). **PARTIAL** against the full original 12.1-12.9 task list in
`IMPLEMENTATION_PLAN.md` - 12.3 (rate-limit tuning) and 12.4 (CI-wired dependency
scanning) remain untouched, 12.9 (external review) remains internal-only. Not BLOCKED -
nothing found this phase requires a decision this session couldn't make.

## Canonical Phase 13 status (Deployment)

**PARTIAL**, unchanged by this phase - see `PHASE_11_REPORT.md`. No production or
staging environment has actually been deployed to; no CD pipeline exists. This phase's
work (export/deletion/V48 migration) has not yet been deployed anywhere either -
`docs/releases/PRODUCTION_RELEASE_CHECKLIST.md` should be run before it is.

## Canonical Phase 14 status (Monitoring)

**PARTIAL**, unchanged by this phase - see `PHASE_11_REPORT.md`. Structured JSON
logging, most named metrics, and error-tracking integration remain unwired. This phase
added no new metrics for export/deletion specifically - a real, disclosed gap (an
`account.export`/`account.deletion` counter pair would be a natural, low-effort future
addition following the exact pattern `SecurityMetricsListener` already established).

## Security / Privacy Readiness

- **Authentication security**: HIGH - unchanged, already strong (Phase 2 foundation,
  re-verified).
- **Authorization security**: HIGH - representative matrix now exists and passes;
  MEDIUM would apply only if claiming exhaustive per-endpoint coverage, which is not
  claimed.
- **Account deletion**: HIGH - real, tested, governance-safe, multi-session-proven,
  transactionally atomic by construction.
- **Personal data export**: HIGH - real, tested, secret-exclusion proven structurally
  and by regression.
- **Data minimization**: MEDIUM - a real audit exists and is honest, but several
  collected fields remain ahead of their eventual Rule use; not fixed this phase.
- **Governance preservation**: HIGH - proven directly (the staff-deletion test), not
  just designed.
- **Logging / backup privacy**: MEDIUM-HIGH - strong, real coverage with two small,
  named, low-severity gaps (DEBUG-local logging, backup-restore reconciliation
  documented but not automated).
- **GDPR technical readiness**: MEDIUM-HIGH - the two headline self-service
  capabilities (access/export, erasure) are real and tested; several areas remain
  explicitly LEGAL_REVIEW_REQUIRED by design, not by omission.

## Next canonical phase recommendation

Per the roadmap, canonical **Phase 13 (Deployment)** already has substantial
foundation work done (ADR-013, Dockerfiles, Compose stack, backup/restore drill - see
`PHASE_11_REPORT.md`); its remaining gap is that nothing has actually been deployed
anywhere. The highest-value next step is standing up the already-documented staging
environment and running `docs/releases/PRODUCTION_RELEASE_CHECKLIST.md` against it for
real, which would also be the natural point to include this phase's V48 migration and
new endpoints in a genuine first release. **Not started. Stopping here per
instruction.**
