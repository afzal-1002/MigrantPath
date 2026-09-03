# Phase 9 Report — Admin Panel + Legal Content Governance

Status: ✅ COMPLETE — 2026-09-03

## Architecture

```text
CONTENT_EDITOR / LEGAL_REVIEWER / ADMIN
        │
   Angular /admin panel
        │
   /api/v1/admin/** (Phase 9, new) ─┬── /api/v1/internal/content/** (Phase 4, unchanged)
        │                           │
   Domain *AdminService             │
   (Procedure/Rule/Threshold/       │
    Questionnaire)                  │
        │                           │
   ContentReviewCoordinator ────────┘   (self-approval prevention, one place, all 4 types)
        │
   PublicationStateMachine + domain *PublishingService/*Service
   (DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED — shared since Phase 4)
        │
   Every mutation ──► AuditService.record ──► AuditLog (append-only)
```

Phase 9 deliberately adds a thin governance layer over the Phase 4-8 lifecycle rather than a
new engine — see [ADR-012](../architecture/ADR/ADR-012-admin-content-governance.md).

## Roles

Full matrix: [docs/admin/ROLE_PERMISSIONS.md](../admin/ROLE_PERMISSIONS.md).

- **CONTENT_EDITOR**: create identities/drafts, edit draft content, attach sources, submit,
  validate, dry-run. Cannot approve, publish, or manage roles.
- **LEGAL_REVIEWER**: view everything CONTENT_EDITOR can, approve/request-changes on a version
  *someone else* submitted, verify sources. Cannot publish or manage roles.
- **ADMIN**: everything above, plus publish, archive, and role management. Never gains implicit
  access to private user data.

## Procedure Management

Identity creation and version content mutation (steps/documents/fees add-edit-remove, sources)
stay on Phase 4's tested `/api/v1/internal/content/**`. Phase 9 adds: list/detail read
endpoints, `PATCH` overview editing (title/summary/description/effectiveFrom/changeSummary —
previously not settable at all through any service method), edit/remove for existing steps and
documents (previously add-only), edit/remove for fees, `copy` (create-new-version-from),
`validate`, `diff`, `impact` (active `UserCase` count per version), and the review-workflow
actions (`submit`/`approve`/`request-changes`) routed through `ContentReviewCoordinator`.

## Rule Management

A structured condition builder (`/admin/rules/:code/versions/:n`) backed by a real Fact
Registry (`GET /api/v1/admin/facts` — every direct Question plus every derived fact, with its
allowed operators) — an author can never type an unknown fact or pick an invalid operator. It
supports one `ALL`/`ANY` group of leaf conditions with a literal-value-or-threshold-reference
toggle per condition; a tree the builder can't represent (`NOT`, nested groups) is detected on
load and the editor falls back to an "Advanced JSON" mode automatically, with a live definition
preview always shown. `Validate` runs the real `ConditionTreeValidator`. `Dry run`
(`POST /api/v1/admin/rules/dry-run`) evaluates an unsaved condition tree against synthetic,
admin-typed facts via `RuleEvaluator.previewEvaluate` (an untouched Phase 6 method, never
exercised through HTTP before this phase) — clearly preview-only, never a real user's
Assessment.

## Source Management

Verification (`VERIFIED`/`NEEDS_REVIEW`/`OUTDATED`/`ARCHIVED`) with full history per source; the
admin UI explains explicitly that `VERIFIED` means the source itself was checked, not that
content built from it is automatically legally approved. "Mark outdated" reuses the same
verification action (status=`OUTDATED` + a reason in notes) rather than a separate endpoint.
Impact (`GET /api/v1/admin/sources/{id}/usage`) shows how many Procedure/Rule/Threshold
versions depend on a source before it's marked outdated.

## Questionnaire Management

Version lifecycle (copy-from-existing → submit → review → publish/archive) plus a read-only
per-version question listing and an impact count (assessments bound to that version). Deep
question/dependency editing through this admin surface is a deliberate scope cut — see
Deviations.

## Review Workflow

One `AdminReview` row opens per `submit`, one closes per `approve`/`request-changes`/`reject`,
shared by all four content types. Self-approval prevention (the account that submitted a
version can never review it) is enforced once, centrally, in `ContentReviewCoordinator` — not
reimplemented per domain. Full policy: [CONTENT_REVIEW_WORKFLOW.md](../admin/CONTENT_REVIEW_WORKFLOW.md).

## Version Diff

- **Procedure**: a genuine stable-code-matched diff (steps/documents/fees added/removed/changed,
  overview field changes) — reuses the exact pattern Phase 8's `CaseRequirementChangeService`
  established.
- **Rule**: a pragmatic diff — whether the condition tree/explanation key changed at all, plus
  both trees shown side by side (the tree is opaque JSON; a misleading field-by-field "semantic"
  diff was judged worse than an honest raw comparison).
- **Threshold/Questionnaire**: no dedicated diff endpoint this phase (see Deviations) — version
  detail views show enough to compare directly (value/dates for Threshold, question lists for
  Questionnaire).

## Impact Analysis

Counts only, never identities: active `UserCase`s per Procedure version, assessments per
Questionnaire version, referencing Rule codes per Threshold, dependent version counts per
Source. No admin endpoint returns which specific user is affected by anything.

## Audit

New `AuditLog` table (`common.audit` package), one write path (`AuditService.record`), a closed
domain-specific action vocabulary (`AuditActionType` — `CONTENT_PUBLISHED`, `SOURCE_VERIFIED`,
`ROLE_ASSIGNED`, ...), append-only, written transactionally alongside the mutation it describes.
Full policy: [AUDIT_POLICY.md](../admin/AUDIT_POLICY.md). **Known gap, disclosed there and in
Deviations below**: content mutated through the pre-existing Phase 4
`/api/v1/internal/content/**` endpoints is not retrofitted into this table.

## Historical Safety Audit

Phase 8 documented that some authority/source metadata is resolved *live* (not snapshotted) for
historical `UserCase` display. This phase's audit outcome: **Phase 9 adds no endpoint that
mutates `OfficialSource.title`/`sourceUrl`, or any `Authority` field at all** — no Authority
admin API was built this phase (reference data stays read-only, reusing the existing public
`/api/v1/reference/**` GETs, per brief §55/§56's own conservative default). New setters added to
`OfficialSource` for `publicationDate`/`effectiveFrom`/`effectiveTo`/`notes` exist on the entity
but are not wired to any admin endpoint yet — genuinely unreachable this phase. The Phase 8
concern is therefore **unchanged, neither worsened nor resolved** by Phase 9: a source's
identity fields (title, URL) can only be set once, at creation, through this phase's API surface.
This remains a real, documented gap for a future phase that adds Authority/Office editing or a
`title`/`sourceUrl` edit action to Sources.

## Conditional Personalization Gap

**Still deferred, unchanged from Phase 8.** Phase 6's `DOCUMENT_REQUIREMENT`/`STEP`/`FEE` Rule
targets remain unresolvable to a specific identity; Phase 9 did not touch `RuleTargetType`
resolution or `UserCaseSnapshotService`'s applicability derivation. The limitation stays fully
visible, never hidden: the admin UI shows a Rule's `targetType` plainly; no conditional
personalization logic was fabricated anywhere in Phase 9.

## Database

**New migration**: `V46__create_admin_governance.sql` — `audit_log` (with indexes on
`(actor_user_id, occurred_at)`, `(entity_type, entity_id)`, `(action_type, occurred_at)`),
`admin_review` (with a unique partial index enforcing one `PENDING` review per version, plus
indexes on `(entity_type, entity_version_id)` and `(status, created_at)`), and
`ALTER TABLE threshold_versions ADD COLUMN submitted_by` (a structural gap vs its three
siblings, closed).

## APIs

69 endpoints under `/api/v1/admin/**` (verified live against the running application's own
OpenAPI document), spanning: `procedures`, `rules`, `facts`, `thresholds`, `sources`,
`questionnaires`, `reviews`, `audit`, `dashboard`, `users`. Full list in each domain's admin
controller; the shape mirrors `AdminProcedureController` (list/detail/copy/update/submit/
approve/request-changes/publish/archive/validate/diff/impact/reviews) across all four content
types, plus `dry-run`/`validate-tree` for Rules and `verify`/`verifications`/`usage` for
Sources.

## Frontend

**Services** (`core/services/admin/`, one per content type per brief §94):
`AdminProcedureService`, `AdminRuleService`, `AdminThresholdService`, `AdminSourceService`,
`AdminQuestionnaireService`, `AdminReviewService`, `AdminAuditService`, `AdminUserService`,
`AdminDashboardService`, `AdminFactService`.

**Routes** (`/admin/**`, guarded by `adminGuard`): dashboard; `procedures` (list/detail/
version-editor with Overview/Steps/Documents/Fees/Sources/Validation/History sections);
`rules` (list/detail/version-editor with the structured condition builder); `thresholds`
(list/detail with inline version editing); `sources` (list/detail with verification history and
impact); `questionnaires` (list/detail); `reviews` (cross-entity queue); `audit` (ADMIN-only,
paginated, filtered); `users` (ADMIN-only role management). The ordinary user shell
(`layout/shell`) shows an "Admin" nav link only to accounts holding an admin role.

## Security

Every `/api/v1/admin/**` action is enforced by a `SecurityConfig` URL matcher (most-specific-
first, the same idiom as Phase 4's `/api/v1/internal/content/**` block) — publish/archive/role-
management/audit are the tightest gates (`ADMIN` only); approve/request-changes/source-verify
need `LEGAL_REVIEWER`/`ADMIN`; everything else under the prefix needs any of the three admin
roles. Self-approval is enforced a second time, centrally, at the service layer
(`ContentReviewCoordinator`) — a role check alone cannot express "not the same person who
submitted this." CSRF remains enabled unchanged (cookie-based, same `X-XSRF-TOKEN` convention).
Optimistic locking (`lockVersion`) is unchanged/reused from Phase 4-8, proven by a dedicated
integration test. No admin endpoint returns Assessment/Recommendation/UserCase content for any
user, verified by inspection of every admin DTO and controller.

## Tests

**Backend: 306 tests total (7 new), 0 failures, Spotless clean** (`./mvnw verify`) — up from
Phase 8's 299. The 7 new tests are one comprehensive `AdminGovernanceIntegrationTest`
(Testcontainers Postgres 18, real HTTP + Spring Security, synthetic `TEST_*` content only)
covering: the full CONTENT_EDITOR→LEGAL_REVIEWER→ADMIN procedure lifecycle including
self-approval rejection and role-gated 403s on publish, an audit-log assertion; the full Rule
lifecycle including validate/dry-run/publish; the Threshold lifecycle; source verification
history + impact (including the `OUTDATED` workflow); optimistic-locking behavior on a draft
overview edit; the cross-entity review queue; and role-management including the
cannot-remove-own-last-ADMIN-role guard. All Phase 1-8 tests (299) remain green in the same run.

**Frontend: 112 tests total (14 new)**, lint clean, build succeeds. New: 5 `adminGuard` tests,
2 `AdminDashboard` tests, 3 `ProcedureAdminList` tests, 4 `RuleVersionEditor` tests (the
structured-builder parse/rebuild/fallback/combine logic).

**Playwright: not exercised this phase** — a deliberate, disclosed scope cut given this
phase's size; see Deviations and Known Issues. The backend integration suite above is real
HTTP+Security+Postgres coverage of the same flows Playwright scenarios would exercise, just not
through a real browser.

## Bugs Found

Real bugs found and fixed while building this phase (not hypothetical):

1. **A LazyInitializationException class of bug, reproduced in four different admin detail
   responses** (Procedure/Rule/Threshold/Questionnaire actor fields; Rule/Source-verification
   nested `officialSource`/`checkedBy` references) — the same category of bug this codebase has
   fixed repeatedly since Phase 3, reproduced fresh in Phase 9's new repository methods and
   fixed the same way: fetch-joining every association a DTO reads, in the repository layer.
2. **A Postgres "could not determine data type of parameter" error** in the audit-log search
   query, specific to nullable `Instant`/`timestamptz` parameters used only inside an
   `(:param IS NULL OR ...)` branch — fixed by substituting a wide-open default date range in
   `AuditService` instead of a null-check branch in JPQL for those two parameters.
3. **Missing getters** discovered while building Phase 9 DTOs: `DocumentRequirementVersion
   .copyRequired`, `SourceVerification.checkedBy`, `OfficialSource.publicationDate/
   effectiveFrom/effectiveTo/notes` — pre-existing gaps, added.
4. **`ThresholdVersion` had no `archive()` transition and no `submitted_by` column at all** -
   a real structural gap versus its three siblings (Procedure/Rule/QuestionnaireVersion),
   closed via V46 plus entity/service methods.
5. **`QuestionnaireVersionService` exposed no `submitForReview`/`approve`/`sendBackToDraft`/
   `archive` at all** - Phase 5 shipped only `createDraftFrom`/`publish` since no admin surface
   existed yet to call the rest; added.
6. **A genuine cross-test-class interaction**: `AdminGovernanceIntegrationTest`'s requests,
   run before `AuthIntegrationTest` in the same cached Spring context, left shared filter-chain
   state such that `AuthIntegrationTest`'s very first request stopped receiving its `XSRF-TOKEN`
   cookie - reproduced deterministically running just the two classes together, absent when
   either runs alone. Root cause not fully isolated within this phase's time budget; fixed
   pragmatically with `@DirtiesContext(AFTER_CLASS)` on the new test class - the standard,
   low-risk remedy for cached-context test pollution, verified to resolve it without weakening
   this phase's own coverage.
7. **Two test-authoring mistakes**, found and fixed before they became false confidence: a
   synthetic condition tree included a `"type"` field `ConditionTreeParser` rejects (a leaf is
   identified purely by having a `"fact"` key); and a dry-run test tried to supply the derived
   fact `AGE_YEARS` directly, which `FactResolver` always recomputes from `DATE_OF_BIRTH`
   instead - switched to an `EXISTS`-based direct-fact assertion, which needs no type-fidelity
   assumptions about JSON-deserialized synthetic facts.

## Manual Verification

Performed against the real local backend (`SPRING_PROFILES_ACTIVE=local`) + the already-running
`docker compose` Postgres 18/Mailpit: confirmed the known `DB_USERNAME`/`DB_PASSWORD` OS
environment-variable shadowing gotcha (documented in LOCAL_SETUP.md) reproduced and was worked
around the documented way; confirmed a fresh startup migrates cleanly to `V46 - create admin
governance`; confirmed `/actuator/health` reports `UP`; confirmed an unauthenticated
`GET /api/v1/admin/procedures` returns 401; confirmed all 69 `/api/v1/admin/**` endpoints are
registered and reachable via the running application's own live OpenAPI document; ran the
database-quality queries below against the real database; stopped the backend process cleanly
afterward (verified no stray `java.exe` process survived).

## Database Quality

Run against the real local Postgres database:

| Check | Result |
|---|---|
| `audit_log` row count | 0 |
| `admin_review` row count | 0 |
| Duplicate `PENDING` reviews per version | 0 |
| Threshold drafts with a `submitted_by` set while still `DRAFT` (should be null until submit) | 0 |
| Published `ProcedureVersion`s missing title/summary | 0 |
| Published `RuleVersion`s | 0 |
| Accounts holding an admin role | 18 (pre-existing test/manual-verification accounts from this session and earlier phases, unrelated to Phase 9's own correctness) |

Zero across every Phase 9 table - the correct, honest outcome given no one has used the live
admin UI against this database yet; the rich, multi-actor, multi-transition scenario is proven
by `AdminGovernanceIntegrationTest`'s Testcontainers run instead.

## Deviations

- **Structured Rule condition builder covers one `ALL`/`ANY` group of leaf conditions**; `NOT`
  and nested groups fall back to an "Advanced JSON" editor automatically. A full nested visual
  tree editor was judged disproportionate for this phase's time budget (brief's own "if strict
  ... complexity, document deferral" allowance, applied here to the builder's scope rather than
  separation-of-duties).
- **`ThresholdVersion` has no version-copy action, no version number, and no VERIFIED-source
  publish gate** - all three are real, pre-existing asymmetries versus Procedure/Rule/
  Questionnaire (Threshold was deliberately built simpler in Phase 4, since no real threshold
  content existed yet). Phase 9 closed the `archive()`/`submitted_by` gaps needed for the review
  workflow to work uniformly, but did not invent a version-number column or a source requirement
  that never existed - see Known Issues.
- **Questionnaire question/dependency editing is read-only through this admin surface** - only
  version-level actions (copy/submit/approve/publish/archive) are exposed; adding/removing/
  reordering questions or editing dependencies requires a future phase's dedicated editor.
- **No dedicated diff endpoint for Threshold or Questionnaire** - their version-detail views are
  simple enough to compare directly without one.
- **Content created through the pre-existing Phase 4 `/api/v1/internal/content/**` endpoints is
  not retrofitted into the new audit trail** - a deliberate choice to avoid destabilizing
  Phase 4's own tested contract; only the new `/api/v1/admin/**` surface writes to `AuditLog`.
  Disclosed in [AUDIT_POLICY.md](../admin/AUDIT_POLICY.md).
- **No `RuleTestCase` persisted entity** (brief §44's "recommended if manageable") - the
  ad-hoc dry-run endpoint covers the same need for this phase's time budget; a persisted,
  reusable test-case library is deferred.
- **No Authority/Office admin editing, no reference-data write API** - reference data (brief
  §55/§56) stays read-only, reusing the existing public `/api/v1/reference/**` GETs; no new
  admin-namespaced read API was built for it either, since the public one already serves the
  purpose.
- **No scheduled-future-publication distinct UI treatment, no content export/import, no
  emergency-withdrawal action beyond the existing `archive`, no draft autosave** - all
  explicitly out of scope per the brief's own boundary list, confirmed genuinely absent by
  inspection.
- **Playwright was not exercised this phase** - given the phase's overall size, backend
  integration coverage (`AdminGovernanceIntegrationTest`) and frontend unit coverage were
  prioritized within the available time; no new e2e scenarios were authored and the existing
  suite was not re-run against this phase's frontend changes. A real, disclosed gap - see Known
  Issues.

## Known Issues

- Playwright e2e coverage for the admin panel does not exist yet (see Deviations) - the highest-
  value follow-up before this UI is considered fully proven end-to-end through a real browser.
- The `AdminGovernanceIntegrationTest`/`AuthIntegrationTest` CSRF-cookie interaction (Bugs Found
  #6) was worked around, not root-caused - if a future test class exhibits the same symptom,
  revisit this rather than assuming `@DirtiesContext` is a universal fix.
- `ThresholdVersion` remains structurally simpler than its three siblings (no version number, no
  VERIFIED-source publish requirement, no version-copy action) - a real, disclosed gap, not
  silently worked around.
- Historical authority/source metadata resolved live for `UserCase` display (a Phase 8 finding)
  remains unresolved - Phase 9 confirmed it is not worsened (no mutating endpoint reaches those
  fields) but did not close it either.
- No formal `RuleTestCase` library, no Authority/Office admin editing, no reference-data write
  API - all documented deferrals above.
- Reference-data flake in `reference-data.spec.ts` (reported in Phases 6-8) - not re-verified
  this phase since Playwright was not run; presumed still present, unrelated to Phase 9.

## Phase 10 Readiness

**READY**, with one caveat: exercise the admin panel through a real browser (Playwright or
manual) before authoring real Warsaw procedure content through it at volume, since that path
has backend integration coverage but no browser-level coverage yet this phase.
