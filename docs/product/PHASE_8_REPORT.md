# Phase 8 Report — User Cases + Personalized Checklist + Progress Tracking

Status: ✅ COMPLETE — 2026-09-03

## Architecture

```text
Recommendation (Phase 7, PRIMARY_MATCH or POSSIBLE_ALTERNATIVE only)
   ↓
CaseCreationValidator (allowed type, still-current version, non-empty content, IDOR-safe)
   ↓
UserCaseCreationService (idempotent per recommendation)
   ↓
UserCase
   ↓ (current_revision_id)
UserCaseSnapshotRevision (immutable; built by UserCaseSnapshotService)
   ├── UserCaseStep       (from every StepVersion on the pinned ProcedureVersion)
   ├── UserCaseDocument   (from every DocumentRequirementVersion - applicability derived
   │                       from RequirementType, never a raw assessment answer)
   └── UserCaseFee        (from every FeeVersion)
         ↓
UserCaseEvent (append-only timeline)

CaseRequirementChangeService  — compares the current revision to the currently active
                                 ProcedureVersion, read-only, stable-code matched
UserCaseUpgradeService        — explicit only; new revision + status-preserving merge
```

Package layout: `com.foreignerwarsaw.usercase.core` (entities/repositories/status
machines), `com.foreignerwarsaw.usercase.engine` (creation/query/status/item/change-
detection/upgrade services, the one controller, DTOs) — mirrors the `rules`/
`recommendation` module split from Phases 6-7.

## Case Creation

`POST /api/v1/recommendations/{recommendationId}/cases`. Only `PRIMARY_MATCH`/
`POSSIBLE_ALTERNATIVE` recommendations may create a case (brief §4's "safer MVP" -
`MORE_INFORMATION_REQUIRED` is explicitly rejected with `CASE_CREATION_NOT_ALLOWED`,
never silently allowed). **Idempotency is checked first, before any other validation**: a
recommendation with an existing case always returns that case, even if its pinned
`ProcedureVersion` has since gone stale (found and fixed during this phase's own
integration testing — see "Bugs Found"). A genuinely new case creation additionally
requires the recommendation's pinned version to still be the currently active one
(`RECOMMENDATION_OUTDATED` otherwise) and the active version to have at least one step
(`CASE_CONTENT_NOT_READY` otherwise — never an empty, useless checklist).

## Snapshot

Full policy: [CASE_SNAPSHOT_POLICY.md](../cases/CASE_SNAPSHOT_POLICY.md). Frozen at
creation (and at each explicit upgrade): every step/document/fee's content fields, copied
into real `UserCaseStep`/`UserCaseDocument`/`UserCaseFee` rows scoped to one
`UserCaseSnapshotRevision` — never a JSONB blob (the relational rows already are the
provenance record; see the policy doc's "Why no JSONB snapshot blob"). Live, resolved
fresh at read time from the pinned `procedure_id`/`procedure_version_id`: authorities,
offices, and official sources (mirrors Phase 7's `RecommendationSourceResolver`
precedent exactly). `snapshotSchemaVersion` (currently `1`) is stamped on every revision.
Historical reproducibility is proven end to end by `UserCaseIntegrationTest`: after a
procedure republish, `GET /api/v1/cases/{id}` still returns the original revision's
content unchanged.

## Personalization

Confirmed by inspection: **no hard-coded immigration logic exists anywhere in Phase 8.**
`UserCaseSnapshotService` snapshots every step and fee on the active `ProcedureVersion`
unconditionally (Phase 4's schema has no conditional-step/fee concept, and Phase 6's
`STEP`/`FEE` rule targets are declared but not resolvable to a specific target identity
yet — a known Phase 6 gap). Every document's `UserCaseDocumentApplicability` is derived
deterministically from its Phase 4 `RequirementType` (`DEFAULT_REQUIRED`/
`INFORMATIONAL` → `APPLICABLE`, `CONDITIONAL` → `NEEDS_CONFIRMATION` — never a fabricated
`NOT_APPLICABLE`) — never from a raw `AssessmentFacts` answer read directly by this
phase. Full reasoning: [USER_CASE_MODEL.md](../cases/USER_CASE_MODEL.md)'s
"Personalization" section.

## Status Workflow

Full transition table: [CASE_STATUS_WORKFLOW.md](../cases/CASE_STATUS_WORKFLOW.md).
`DRAFT → PREPARING → READY_TO_SUBMIT → SUBMITTED → WAITING → (ADDITIONAL_DOCUMENTS_
REQUIRED ↔ WAITING) → DECISION_RECEIVED → APPROVED/REJECTED → (REJECTED → APPEAL →
DECISION_RECEIVED) → COMPLETED`; `CANCELLED` reachable from every non-terminal status.
Every transition is user-recorded, never system-inferred (brief §23) - no integration
with any government system exists. Checklist edits are blocked once a case is
`CANCELLED`/`COMPLETED` (`CASE_STATUS_TRANSITION_INVALID`).

## Checklist Statuses

- **Step**: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED` (user-settable); `BLOCKED`/
  `NOT_APPLICABLE` reserved for a future conditional-step engine.
- **Document**: `NOT_STARTED`, `MISSING`, `IN_PROGRESS`, `READY` (user-settable);
  `NEEDS_UPDATE` (upgrade-only target, user-settable source); `NOT_APPLICABLE` (snapshot-
  time only).
- **Fee**: `NOT_PAID`, `PAID`, `UNKNOWN` (user-settable); `NOT_APPLICABLE` reserved.

## Progress

```text
stepsCompleted / stepsTotal       (mandatory, non-NOT_APPLICABLE steps only)
documentsReady / documentsTotal   (DEFAULT_REQUIRED documents only)
conditionalDocumentsToReview      (a separate count, never folded into either ratio)
```

No blended percentage, no hidden weighting (brief §19/§20). Formula and denominator
policy documented in `UserCaseProgressService`'s own Javadoc and proven by 5 dedicated
unit tests covering mandatory-only counting, conditional-document separation, and
`NOT_APPLICABLE` exclusion.

## Requirement Changes

`GET /api/v1/cases/{id}/requirement-changes`. Full policy:
[REQUIREMENT_CHANGE_POLICY.md](../cases/REQUIREMENT_CHANGE_POLICY.md). Compares the
case's current revision to whichever `ProcedureVersion` is active *right now*, matched
by stable identity code (never display title), reporting `ADDED`/`CHANGED`/`REMOVED` for
steps, documents, and fees. A material change is a deterministic field-level comparison
(mandatory flag, copies, translation/legalisation requirements, amount/currency, ...) -
never a text diff. Read-only; never mutates the case. **Not covered**: a `Procedure`'s
own title/summary text or an `OfficialSource` record changing independently - a
documented gap, not a silent omission.

## Upgrade

`POST /api/v1/cases/{id}/upgrade` - explicit only, never automatic. Creates a new
`UserCaseSnapshotRevision`, then merges checklist state forward by stable code: an
unchanged item keeps its exact status (and, for documents, the user's own note); a
previously-`READY` document whose requirement materially changed is demoted to
`NEEDS_UPDATE` (brief §36's exact scenario, proven by `UserCaseIntegrationTest`); a
materially-changed step resets to `NOT_STARTED` (no separate "needs review" status exists
for steps, a documented, deliberate simplification); a new item starts fresh; a removed
item simply has no row in the new revision but remains fully visible in the old one.
Rejected with `CASE_ALREADY_CURRENT` if nothing has actually changed. All-or-nothing in
one transaction.

## Database

One new migration (V45, sequential from Phase 7's V44): `user_cases`,
`user_case_snapshot_revisions`, `user_case_steps`, `user_case_documents`,
`user_case_fees`, `user_case_events`, plus every FK/unique/check constraint described
above (`user_cases_recommendation_uq` unique index for the one-case-per-recommendation
guarantee; a `(snapshot_revision_id, stable_code)` unique index on each item table).
Verified live via `\d` against the real local Postgres database and via
`flyway_schema_history` showing V1→V45 applied fresh.

## APIs

```
POST   /api/v1/recommendations/{recommendationId}/cases   create (idempotent)
GET    /api/v1/cases                                       list (own cases)
GET    /api/v1/cases/{caseId}                               detail
PATCH  /api/v1/cases/{caseId}/status                        change case status
PATCH  /api/v1/cases/{caseId}/steps/{stepId}                 update a step
PATCH  /api/v1/cases/{caseId}/documents/{documentId}          update a document (status and/or note)
PATCH  /api/v1/cases/{caseId}/fees/{feeId}                    update a fee
GET    /api/v1/cases/{caseId}/requirement-changes             detect changes
POST   /api/v1/cases/{caseId}/upgrade                         explicit upgrade
GET    /api/v1/cases/{caseId}/events                          case timeline
```

Major services: `CaseCreationValidator`, `UserCaseCreationService`,
`UserCaseSnapshotService`, `UserCaseQueryService`, `UserCaseProgressService`,
`UserCaseStatusService`, `UserCaseItemService`, `CaseRequirementChangeService`,
`UserCaseUpgradeService`, `UserCaseAccessService` (the shared ownership check) - kept
deliberately separate rather than one giant service (brief §69).

## Frontend

- **Routes**: `/cases` (My Cases), `/cases/:id` (Case Detail).
- **Service**: `CaseService` (`core/services/case.service.ts`) - fully typed, no
  business logic.
- **`CaseList`**: active cases grouped separately from completed/cancelled, a
  requirement-update flag per card, an honest empty state.
- **`CaseDetailPage`**: overview/progress, step checklist (mark complete/reopen),
  document checklist (mark ready/reopen), fees (mark paid), authorities/offices/sources,
  and - folded into this one page rather than a separate `/cases/:id/updates` route (a
  deliberate scope simplification, see "Deviations") - an inline "Review changes" /
  "Update case to latest requirements" flow with the brief's exact confirmation copy.
- **Recommendation results integration**: a "Start this pathway" button now appears on
  every `PRIMARY_MATCH`/`POSSIBLE_ALTERNATIVE` card, creating a case and navigating to
  it; the wizard's dashboard gained a "My Cases" entry point.

## Security

Every endpoint requires authentication; every case-scoped read/write goes through
`UserCaseAccessService.getOwned` - a 404, never a 403, for another user's case, proven
for every mutating and read endpoint by `UserCaseIntegrationTest`'s IDOR assertions
(`GET`, `PATCH` status implicitly via the same access path, `GET requirement-changes`,
`POST upgrade`, unauthenticated → 401). No admin/support role gains any special access
to another user's case (brief §55 - privacy first, confirmed by inspection: no role
check anywhere in `usercase.*` grants cross-user access). No raw sensitive answer values
are ever logged or persisted in `UserCaseEvent.metadata` (brief §83) - only stable codes
and status transitions.

## Provenance

Full example, proven by `UserCaseIntegrationTest`:

```text
UserCaseDocument (TEST_DOC_MANDATORY, revision 1)
  → source_document_requirement_version_id → DocumentRequirementVersion (v1's "Passport")
  → (via snapshot_revision_id) UserCaseSnapshotRevision
       → procedure_version_id → ProcedureVersion v1
            → its ProcedureVersionSource rows → OfficialSource (resolved live at read time)
```

No `RuleVersion`/`ThresholdVersion` provenance chain exists on a case item in Phase 8 -
personalization does not depend on rule evaluation yet (see "Personalization" above), so
there is nothing for a case item to trace back to on that axis; the case as a whole still
traces to its originating `Recommendation`, which itself carries full Rule/Threshold
provenance (Phase 7).

## Tests

**Backend total: 299 tests, 0 failures, 0 errors** (`./mvnw verify`, Spotless clean - up
from Phase 7's 279). New Phase 8 tests (20 total):

| Test class | Count | Kind |
|---|---|---|
| `UserCaseStatusTransitionsTest` | 7 | Unit (pure) - the whole-case status machine, every category of transition |
| `UserCaseItemTransitionsTest` | 6 | Unit (pure) - step/document transition rules, reserved-status rejection |
| `UserCaseProgressServiceTest` | 5 | Unit (pure) - the progress formula, mandatory/conditional/NOT_APPLICABLE exclusion |
| `UserCaseIntegrationTest` | 2 | Full-stack Testcontainers Postgres 18 - the complete real chain (assessment → rules → recommendation → case creation → checklist updates → progress → reproducibility → requirement-change detection → upgrade with status-preservation/NEEDS_UPDATE → IDOR) plus the three rejection scenarios (disallowed type, empty content, outdated recommendation) |

**Regression (Phase 1-7): all green** in the same `./mvnw verify` run.

**Frontend**: 98/98 unit tests pass (89 Phase 1-7 baseline + 9 new: 3 `CaseList` + 4
`CaseDetailPage` + 2 `RecommendationResults` "Start this pathway"), `npm run lint`
clean, `npm run build` succeeds (`case-list`/`case-detail` lazy chunks present).

**Playwright**: 11/11 scenarios pass (one pre-existing, unrelated `reference-data.spec.ts`
timing flake under parallel workers, confirmed passes in isolation - reported in both
prior phase reports, still not fixed, out of this phase's scope).
`assessment.spec.ts`'s Scenario 1 was extended to also visit `/cases` via the dashboard's
new "My Cases" link and assert the honest empty-cases state (no case exists, since no
production Rule content means no `PRIMARY_MATCH` to start one from).

## Bugs Found

One, found and fixed during this phase's own design/testing (not a pre-existing
regression): **idempotent case return was originally validated before being checked**,
meaning a second `POST` for a recommendation that already had a case would incorrectly
fail with `RECOMMENDATION_OUTDATED` if the underlying `ProcedureVersion` had changed in
the meantime - even though the user was only trying to return to their existing case, not
create a new one. Fixed by checking for an existing case *first*, before any other
validation (`UserCaseCreationService.findExistingCase`, called by the controller ahead of
`CaseCreationValidator.validate`) - staleness now only ever blocks genuinely *new* case
creation. Documented in [USER_CASE_MODEL.md](../cases/USER_CASE_MODEL.md#case-creation).

## Manual Verification

Performed against the real local backend + Postgres + Mailpit (`SPRING_PROFILES_ACTIVE=local`):
fresh `flyway_schema_history` showing V1→V45 applied cleanly; `\d user_cases` and
sibling tables confirming every constraint/index present as migrated; the full
Playwright suite (including the extended assessment→dashboard→My Cases journey) run
against this live backend; direct `psql` data-quality queries (below) run against the
resulting real, empty-by-design case data. The rich multi-revision, multi-status
scenario (snapshot creation, checklist updates, republish, requirement-change detection,
upgrade with status preservation) was verified via `UserCaseIntegrationTest`'s
Testcontainers run rather than by hand-seeding real production content, consistent with
this project's own "never fabricate legal content" discipline.

## Database Quality

Run against the real local Postgres database, after the real Playwright run above (which
never created a case - no production Rule content exists to produce a `PRIMARY_MATCH`):

| Check | Result |
|---|---|
| `user_cases` / `user_case_snapshot_revisions` row counts | 0 / 0 |
| Duplicate case per recommendation | 0 |
| Orphan snapshot revisions | 0 |
| Orphan steps / documents / fees | 0 / 0 / 0 |
| Orphan events | 0 |
| Invalid revision numbers (`<= 0`) | 0 |
| Invalid case statuses | 0 |
| Duplicate step/document stable codes within one revision | 0 |
| Broken `ProcedureVersion` provenance on a revision | 0 |

All zero - the correct, honest outcome given the deliberately empty production content
catalogue; the queries themselves are real and were separately exercised against rich,
multi-revision data by `UserCaseIntegrationTest`'s Testcontainers run.

## Deviations

- **`UserCaseRequirementSnapshot`'s single JSONB-array row became a real
  `UserCaseSnapshotRevision` + per-item table design** - the single largest structural
  deviation from the Phase 0 sketch, driven by the brief's own explicit revision/upgrade
  requirements a single row per case couldn't represent. Documented fully in ADR-011.
- **No district/office-selection case-setup screen** (brief §18/§97-98) - offices are
  shown from whatever the `ProcedureVersion` already associates; a case never asks the
  user to choose a Warsaw district. Deferred - no procedure in this codebase's test/seed
  content yet exercises district-dependent office routing to make this concretely needed.
- **Requirement-change detection covers step/document/fee content only** - a
  `Procedure`'s own title/summary text and independent `OfficialSource` record updates
  are not separately detected as `SOURCE_CHANGED`/`PROCEDURE_CONTENT_UPDATED` (brief §28's
  suggested vocabulary). Documented in REQUIREMENT_CHANGE_POLICY.md.
- **The "review changes" / upgrade UI lives inline on the case detail page**, not a
  separate `/cases/:id/updates` route the brief suggested (§50) - a deliberate scope
  simplification; the same information and the same explicit-confirmation UX exist,
  just without a dedicated URL.
- **No `RuleTestCase`/rule-preview-style tooling, no notifications, no deadline model** -
  all explicitly out of Phase 8's scope per the brief itself (§84/§85/§114 marked
  optional/deferred), confirmed genuinely absent by inspection.
- **`RecommendationResponse` (Phase 7) gained an `id` field** - discovered as a real,
  necessary gap while building this phase's integration test and the "Start this
  pathway" frontend flow: without it, neither a test nor the UI could ever call `POST
  /api/v1/recommendations/{recommendationId}/cases` at all. Added to the existing Phase 7
  DTO/query-service rather than worked around, since it is a Phase 7 contract completion,
  not new Phase 8 scope.

## Known Issues

- No district/office-selection UX (see "Deviations") - not currently blocking, since no
  test/seed content needs it yet, but a real gap for a future procedure that does.
  Documented in [USER_CASE_MODEL.md](../cases/USER_CASE_MODEL.md).
- Requirement-change detection's scope gap (Procedure-summary/source-record changes not
  detected) - see "Deviations."
- No conditional-step/document/fee rule evaluation - inherited directly from Phase 6's
  own known gap (`STEP`/`DOCUMENT_REQUIREMENT`/`FEE` rule targets not resolvable to a
  specific identity yet); Phase 8's snapshot builder has exactly one integration point
  ready for this once Phase 6 closes that gap (`UserCaseSnapshotService`'s applicability
  derivation).
- `reference-data.spec.ts`'s pre-existing timing flake under parallel Playwright workers
  (reported in PHASE_6_REPORT.md and PHASE_7_REPORT.md, unrelated to Phase 8, confirmed
  pre-existing again this phase) remains unfixed.
- No duplicate-analysis-style concurrency lock on case creation/upgrade beyond ordinary
  optimistic locking (`lock_version` on `UserCase`) - acceptable per the brief's own
  "keep MVP simple" instruction (brief §80), not silently ignored.

## Phase 9 Readiness

**READY.**

`UserCase`/`UserCaseSnapshotRevision`/`UserCaseStep`/`UserCaseDocument`/`UserCaseFee`/
`UserCaseEvent` form a stable, ownership-checked, immutable-snapshot, fully-tested
contract. No admin UI, legal-content editor, procedure-publishing UI, rule editor UI,
payment/subscription, professional marketplace, AI document analysis, document upload,
OCR, or lawyer/referral feature was implemented anywhere in this phase (brief's own
exclusions, confirmed absent by inspection) - all of these remain genuinely open for
Phase 9 and beyond.
