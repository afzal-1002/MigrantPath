# Phase 7 Report — Recommendation Engine

Status: ✅ COMPLETE — 2026-09-03

## Architecture

```text
Assessment
   ↓
AssessmentFacts
   ↓
RuleEvaluationBundle (Phase 6, grouped by target procedure)
   ↓
RecommendationService (orchestrator)
   ├── RecommendationClassifier    (per-procedure RuleEvaluationResults -> RecommendationType)
   ├── RecommendationRanker        (deterministic ordering + PRIMARY/ALTERNATIVE split)
   ├── RecommendationReasonMapper  (Phase 6 ConditionTraces -> stable reason codes)
   └── RecommendationSourceResolver (read-time, deduplicated OfficialSource resolution)
            ↓
RecommendationRun (immutable) -> Recommendation (per procedure) -> RecommendationReason (per condition)
```

Package layout: `com.foreignerwarsaw.recommendation.core` (persisted entities/repositories),
`com.foreignerwarsaw.recommendation.engine` (classifier/ranker/reason-mapper/source-resolver,
the orchestrating `RecommendationService`, the read-side `RecommendationQueryService`, the one
controller, and DTOs) - mirrors Phase 6's `rules.core`/`rules.evaluation` split.

Phase 6 remains the sole eligibility authority throughout: no class in `recommendation.*` ever
reads `AssessmentFacts` or evaluates a condition itself - every classification decision is a
pure function of already-computed `RuleEvaluationResult`s (brief §14).

## Recommendation Categories

Full exhaustive table in [RECOMMENDATION_POLICY.md](../recommendations/RECOMMENDATION_POLICY.md).
Summary:

- **`PRIMARY_MATCH`** - every required rule (`ELIGIBILITY`/`APPLICABILITY`/`REQUIREMENT`)
  `SATISFIED`, no `EXCLUSION` rule `SATISFIED` or `INDETERMINATE`, ranked at the top tier.
- **`POSSIBLE_ALTERNATIVE`** - a match-candidate demoted by `RecommendationRanker` only when a
  reviewed `Procedure.recommendationPriority` exists and ranks it below another candidate (never
  produced by the classifier itself).
- **`MORE_INFORMATION_REQUIRED`** - a required or exclusion rule is `INDETERMINATE`.
- **`NOT_APPLICABLE`** - a required rule is `NOT_SATISFIED`, or an exclusion rule is `SATISFIED`.
- **`UNAVAILABLE_FOR_ANALYSIS`** - any rule for this procedure returned `ERROR`, or no active
  `PUBLISHED` `ProcedureVersion` exists at the evaluation date - never silently folded into any
  other category (brief §48/§118).

No numeric confidence, percentage, or probabilistic language exists anywhere in the model
(verified by grep across `recommendation.*` - no `double`/`float`/percentage field).

## Aggregation Policy

Phase 6's `RuleType` is reused directly as the "rule role" the brief asked for (brief §16) -
no parallel field was introduced. `ELIGIBILITY`/`APPLICABILITY`/`REQUIREMENT` are "required,"
`EXCLUSION` is exclusionary, `INFORMATION_REQUIRED` never gates the outcome (its conditions still
produce reasons). `RecommendationClassifier` combines these with an `ERROR`-anywhere check that
always wins first. Full combination table in RECOMMENDATION_POLICY.md; every branch is covered
by `RecommendationClassifierTest` (10 tests).

## Ranking

Full policy in [RANKING_POLICY.md](../recommendations/RANKING_POLICY.md). Deterministic, two
inputs: (1) fixed category precedence (`PRIMARY_MATCH` > `POSSIBLE_ALTERNATIVE` >
`MORE_INFORMATION_REQUIRED` > `NOT_APPLICABLE` > `UNAVAILABLE_FOR_ANALYSIS`), (2) within the
match category only, an optional reviewed `Procedure.recommendationPriority` (nullable, unset
for every real procedure today - brief §24's "do not encode real legal policy without
source/review" followed exactly, extending Phase 6's own no-fabrication discipline to the
ranking layer). Final tie-break is always the Procedure's stable `code`, alphabetically (brief
§44) - never database/collection retrieval order. No percentage, no AI, no arbitrary weighting.
Every branch (category ordering, no-priority-declared, priority-declared demotion, tie-breaking,
demotion never applying outside `PRIMARY_MATCH`) is covered by `RecommendationRankerTest`
(5 tests).

## Persistence

`RecommendationRun` (one per analysis, immutable once `status` leaves `RUNNING`) ->
`Recommendation` (one per candidate procedure, unique per `(run, procedure)`) ->
`RecommendationReason` (one per condition trace, cascade-deleted with its recommendation).
Nothing is ever updated after a run completes - re-analysis (`POST
.../recommendation-runs`) always inserts a brand-new `RecommendationRun`, proven by
`RecommendationEngineIntegrationTest`'s republish scenario. `RecommendationRunStatus`
(`RUNNING`/`COMPLETED`/`PARTIAL`/`FAILED`) and `RecommendationType` are both plain enums backed
by DB `CHECK` constraints, matching every other status enum in this codebase.

## Historical Reproducibility

Example, proven end to end by `RecommendationEngineIntegrationTest`:

```text
Run 1: evaluationDate = 2026-09-03, RuleVersion v1 active -> TEST_MATCH_PROCEDURE = PRIMARY_MATCH

A new RuleVersion v2 is drafted, sourced, reviewed, and published with
effectiveFrom = 2026-10-03 (Run 1's rule no longer matches this applicant).

GET /api/v1/recommendation-runs/<run-1-id>  -> still PRIMARY_MATCH (unchanged)

Run 2: evaluationDate = 2026-10-03, RuleVersion v2 active -> TEST_MATCH_PROCEDURE != PRIMARY_MATCH
```

This is the direct consequence of ADR-010's decision to make `RecommendationRun` append-only
rather than the Phase 0 sketch's replace-in-place cache - re-reading an old run never
re-evaluates anything, it returns exactly the `procedure_version_id`/`rule_version_id` pointers
and classification stored at the time.

## Missing Information

Example (from the integration test's `TEST_MORE_INFO_PROCEDURE` scenario - `MONTHLY_GROSS_SALARY`
was never answered in the minimal completion path):

```json
{
  "procedureCode": "TEST_MORE_INFO_PROCEDURE",
  "recommendationType": "MORE_INFORMATION_REQUIRED",
  "missingFacts": ["MONTHLY_GROSS_SALARY"],
  "reasons": [
    { "reasonType": "MISSING_INFORMATION", "reasonCode": "root", "factCode": "MONTHLY_GROSS_SALARY" }
  ]
}
```

`missingFacts` is computed at read time as the distinct `factCode`s of that recommendation's
`MISSING_INFORMATION` reasons (never a separately-persisted, driftable copy). All synthetic test
data - no real legal content exists to report a real example against (see "Seed Data").

## Source Provenance

`Recommendation -> ProcedureVersion -> its OfficialSource associations`, plus `Recommendation ->
RecommendationReason -> RuleVersion -> its OfficialSource associations`, deduplicated by source
id and sorted `LEGAL_BASIS`/`PRIMARY` first (`RecommendationSourceResolver`, computed at read
time from the immutable version ids already stored - see "Deviations" for why this isn't its own
persisted join table, and for the one gap: threshold-version sources aren't included).

## Database

Three new migrations (V42-V44), sequential from Phase 6's last (V41):

- **V42 `create_recommendation_run.sql`** - `recommendation_runs` (user/assessment FKs
  `ON DELETE CASCADE`, `evaluation_date`, `status` CHECK, two engine-version columns,
  `created_at`/`completed_at`), two indexes for the user- and assessment-scoped read paths.
- **V43 `create_recommendation.sql`** - `recommendations` (`recommendation_run_id` FK CASCADE,
  `procedure_id`/`procedure_version_id` FKs, `recommendation_type` CHECK, `rank` CHECK `> 0`,
  unique `(recommendation_run_id, procedure_id)`), `recommendation_reasons` (`recommendation_id`
  FK CASCADE, `reason_type` CHECK, `rule_version_id` FK `ON DELETE SET NULL`, `condition_code`/
  `fact_code`/`message_key`/`display_order`).
- **V44 `add_procedure_recommendation_priority.sql`** - `procedures.recommendation_priority
  INTEGER`, nullable, unpopulated by any migration (see "Ranking").

Verified live against the real local Postgres 18 database (`flyway_schema_history` through V44;
data-quality queries below) - all constraints, indexes, and FKs present exactly as migrated.

## APIs

```
POST   /api/v1/assessments/{id}/recommendation-runs         analyze (creates a new immutable run)
GET    /api/v1/assessments/{id}/recommendations/latest      the most recent run for this assessment
GET    /api/v1/assessments/{id}/recommendation-runs         history, most recent first (summaries)
GET    /api/v1/recommendation-runs/{runId}                  one specific run by id
```

All four require authentication; the two assessment-scoped routes reuse
`AssessmentService#getOwned` exactly like every other `/api/v1/assessments/{id}/...` endpoint;
`/recommendation-runs/{id}` enforces ownership inside `RecommendationQueryService` since it
isn't assessment-path-scoped. `POST .../recommendation-runs` further requires
`Assessment.status == COMPLETED` (`ASSESSMENT_NOT_COMPLETED`, 409, otherwise). No admin/
management API exists for recommendation policy configuration (brief §137 - deferred to Phase 9,
consistent with every prior phase's precedent).

## Frontend

- **Route**: `/assessment/:id/results` (authenticated, `authGuard`).
- **Service**: `RecommendationService` (`core/services/recommendation.service.ts`) -
  `analyze`/`getLatest`/`getHistory`/`getRun`, fully typed, no business logic.
- **Component**: `RecommendationResults` (`features/recommendations/recommendation-results/`) -
  loads the latest run on entry, auto-triggers the first analysis on a 404
  `RECOMMENDATION_RUN_NOT_FOUND` (single-navigation UX per brief §88), shows a `PARTIAL`-status
  warning banner, groups recommendations into "Most relevant" / "Other possible pathways" / "We
  need more information" / a collapsed "Other evaluated pathways" `<details>`, a "Run analysis
  again" action that always calls `analyze()` (never mutates the shown run), a cautious-language
  disclaimer, and the brief's exact empty-result copy when nothing matches. Every card links to
  `/procedures/:code` rather than duplicating procedure content.
- **Wizard integration**: the assessment-wizard's completion screen's placeholder text
  ("Pathway analysis will be available in the next implementation phase") was replaced with a
  real "Analyze my pathways" link to the new route.
- **Language discipline**: `PRIMARY_MATCH` renders as "Most relevant," never "You qualify" or a
  percentage - proven by a dedicated component test asserting no `\d+%` pattern ever appears.

## Security

Ownership and authentication mirror every prior phase's discipline exactly - a 404, never a 403,
for another user's assessment or run (`RecommendationEngineIntegrationTest`'s IDOR assertions:
`GET /recommendation-runs/{id}`, `GET .../recommendations/latest`, and `POST
.../recommendation-runs` all return 404 for an intruder; an unauthenticated request to
`/recommendation-runs/{id}` returns 401). CSRF is unaffected - the one mutating endpoint
(`POST .../recommendation-runs`) goes through the existing cookie-CSRF stack with no
special-casing. No personal answer *values* are ever persisted in `RecommendationReason` - only
stable codes (`conditionCode`/`factCode`), matching brief §55's privacy requirement; the
underlying `Assessment` remains the single source of truth for actual answer values.

## Seed Data

- **REAL VERIFIED**: none. No production `Rule`/`Procedure`/`Threshold`/`recommendation_priority`
  content was published or set (unchanged from Phase 6's own policy).
- **DRAFT**: none.
- **TEST-ONLY**: synthetic `TEST_*`-coded procedures/rules created only inside
  `RecommendationEngineIntegrationTest`, never in a Flyway migration.

A real, unattended run of the whole stack against the actual local dev database (see
"Verification") produced exactly one `RecommendationRun`, `COMPLETED`, with zero recommendations
- the honest, correct outcome given the genuinely empty production catalogue, confirmed by the
Playwright scenario asserting the "couldn't identify a matching pathway" empty state.

## Tests

**Backend total: 279 tests, 0 failures, 0 errors** (`./mvnw verify`, Spotless clean - up from
Phase 6's 255). New Phase 7 tests (24 total):

| Test class | Count | Kind |
|---|---|---|
| `RecommendationClassifierTest` | 10 | Unit (pure) - every branch of the classification table |
| `RecommendationRankerTest` | 5 | Unit (pure) - category ordering, priority-driven demotion, no-priority default, tie-breaking |
| `RecommendationReasonMapperTest` | 7 | Unit (pure) - every reason-type mapping, exclusion-specific handling, deterministic ordering |
| `RecommendationEngineIntegrationTest` | 2 | Full-stack Testcontainers Postgres 18 - one classification per `RecommendationType` in a single run (incl. the `PARTIAL` status), historical reproducibility across a rule republish, IDOR/ownership across all four endpoints, the completed-assessment gate |

**Regression (Phase 1-6): all green** in the same `./mvnw verify` run.

**Frontend**: 89/89 unit tests pass (82 Phase-1-6 baseline + 7 new `RecommendationResults`
tests), `npm run lint` clean, `npm run build` succeeds (new `recommendation-results` lazy
chunk present).

**Playwright**: 11/11 scenarios pass (one pre-existing, unrelated `reference-data.spec.ts`
timing flake under parallel workers, confirmed passes in isolation - same flake reported in
PHASE_6_REPORT.md, still not fixed, out of this phase's scope). `assessment.spec.ts`'s Scenario
1 was extended to click "Analyze my pathways," land on `/assessment/:id/results`, and assert the
honest empty-result state plus the absence of any `\d+%` confidence figure anywhere on the page.

## Bugs Found

One, found and fixed during this phase's own integration test authoring (not a pre-existing
regression): the original `TEST_UNAVAILABLE_PROCEDURE` scenario referenced `MONTHLY_GROSS_SALARY`
in its threshold-comparison leaf, but that fact was never answered in the test's minimal
completion path - `RuleEvaluator`'s MISSING-before-comparison precedence (correct Phase 6
behavior) meant the rule returned `MISSING`/`INDETERMINATE`, not the intended `ERROR`, so the
run's status came back `COMPLETED` instead of the expected `PARTIAL`. Not an application bug -
the test itself was targeting the wrong fact. Fixed by using `DATE_OF_BIRTH` (always answered in
the minimal completion path) instead, correctly isolating the "threshold has no active
published version" `ERROR` path from the unrelated "fact never answered" `MISSING` path.

## Verification

Commands actually executed, in order:

```bash
cd backend && ./mvnw -o compile                      # after each logical batch of new files
cd backend && ./mvnw -o test-compile
cd backend && ./mvnw -o test -Dtest=<new classes>     # incremental, before the full suite
cd backend && ./mvnw -o spotless:apply
rm -rf backend/target                                 # Windows file-lock on `clean`, per CLAUDE.md
cd backend && ./mvnw -o verify                        # 279 tests, 0 failures - BUILD SUCCESS
cd frontend && npm run lint                            # clean
cd frontend && npm test -- --no-watch                  # 89/89 passed
cd frontend && npm run build                           # succeeded
docker compose up -d                                   # Postgres 18 + Mailpit (already running)
# killed a stale Phase-6-era `spring-boot:run` process still on port 8080, restarted fresh
cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw -o spring-boot:run   # V1->V44 applied fresh
curl http://localhost:8080/actuator/health              # UP
cd frontend && npm run e2e                              # 11/11 passed (1 flaky-under-load, isolated pass confirmed)
docker exec ... psql ... <data-quality queries>          # see below
docker exec ... psql -c "SELECT ... flyway_schema_history"  # V42-V44 confirmed applied
```

## Data Quality

Run against the real local Postgres database, after the real Playwright run above produced one
genuine `RecommendationRun` row:

| Check | Result |
|---|---|
| `recommendation_runs` / `recommendations` / `recommendation_reasons` row counts | 1 / 0 / 0 |
| Duplicate `(recommendation_run_id, procedure_id)` | 0 |
| Invalid ranks (`<= 0`) | 0 |
| Orphan `recommendations` (run doesn't exist) | 0 |
| Orphan `recommendation_reasons` (recommendation doesn't exist) | 0 |
| Non-`UNAVAILABLE_FOR_ANALYSIS` recommendation with no `procedure_version_id` | 0 |
| Invalid `recommendation_runs.status` | 0 |
| Invalid `recommendations.recommendation_type` | 0 |
| `COMPLETED`/`PARTIAL` runs missing `completed_at` | 0 |
| Runs stuck in `RUNNING` | 0 |
| Duplicate `(recommendation_id, reason_code, condition_code)` | 0 |

All zero - the one real run (zero recommendations, correctly, given the empty production
catalogue) violates nothing; the queries themselves are real and would catch a violation in
real content just as readily, and were separately exercised against rich, multi-category data
by `RecommendationEngineIntegrationTest`'s Testcontainers run.

## Performance

Not separately load-tested (no real recommendation volume exists yet). By construction,
`RecommendationService.evaluateAndRank` issues one Phase 6 `evaluateApplicableRules` call (which
already batches its own rule/threshold/country-group resolution per Phase 6's own performance
notes), then one `Procedure`/`ProcedureVersion` lookup per candidate target code - no per-reason
or per-source database call during evaluation; `RecommendationSourceResolver` batches per
recommendation at read time, not per reason. `RecommendationEngineIntegrationTest`'s full
four-procedure lifecycle-plus-two-analyses scenario completed in ~7.5s excluding Testcontainers
startup overhead already amortized by the surrounding suite.

## Deviations

- **`RecommendationRun`/immutable history replaces the Phase 0 DATABASE.md §6 sketch's
  replace-in-place cache** - the single largest, most deliberate deviation, driven directly by
  the approved Phase 7 brief's explicit reproducibility requirements (brief §37/§61/§120). Fully
  documented in ADR-010.
- **Threshold-version sources are not included in a recommendation's resolved `officialSources`**
  - only `ProcedureVersion` and `RuleVersion` sources are. Including threshold sources would need
  persisting which `ThresholdVersion`s a recommendation's rules actually used (Phase 6's
  `ThresholdUsage` is a transient, in-memory-only record, never persisted) - a further child
  table for a case no production `Threshold` content exists yet to exercise. Noted as a real,
  if currently inconsequential, gap.
- **Alternative legal bases (Rule A *or* Rule B for the same procedure) are not modeled** - each
  `RuleVersion` is still a standalone tree (ADR-009's own scope, carried forward). A procedure
  needing this shape is a future, deliberate design decision, not guessed at here (brief §19's
  own "do not create contradictory second-layer logical semantics unnecessarily").
- **`recommendationPriority`-driven `POSSIBLE_ALTERNATIVE` demotion exists but is unpopulated**
  for every real procedure (brief §24) - proven correct via `RecommendationRankerTest` with
  synthetic priorities, never exercised with real data because none exists yet to review.
  Consequently **`POSSIBLE_ALTERNATIVE` cannot currently be produced against real content** -
  an honest, documented gap rather than a fabricated ranking signal.
- **No i18n framework exists yet anywhere in this app** (unchanged since Phase 5) -
  `RecommendationResults` maps `reasonType`/`recommendationType` to a small local English
  fallback string rather than a real `messageKey` translation lookup. `messageKey` is still
  correctly threaded through the whole backend chain (`LeafCondition.explanationKey` ->
  `RuleEvaluationResult`/`ConditionTrace` -> `RecommendationReason.messageKey` -> API response)
  for a future i18n phase to consume without a backend change.
- **`RECOMMENDATION_ENGINE_VERSION`** implemented as `RecommendationService.ENGINE_VERSION`
  (currently `"1"`), stored per-run alongside `RuleEvaluator.ENGINE_VERSION` - two independent
  stamps, as the brief asked for (§21).
- **No admin/management API for recommendation policy** - matches every prior phase's precedent
  (brief §137, deferred to Phase 9).
- **No concurrency/duplicate-analysis lock** was added (brief §71/§72 explicitly permits this
  for MVP) - two near-simultaneous `POST .../recommendation-runs` calls for the same assessment
  would each create their own valid, independent `RecommendationRun` rather than being
  deduplicated. Acceptable per the brief's own "keep MVP simple" instruction; noted as a known
  gap, not silently ignored.

## Known Issues

- `POSSIBLE_ALTERNATIVE` is currently unreachable against real content (see "Deviations" -
  `recommendationPriority` is never populated by real content yet).
- Threshold-version sources are missing from a recommendation's resolved source list (see
  "Deviations").
- No i18n pipeline - `messageKey` is correctly produced end to end but not yet consumed by the
  frontend (see "Deviations").
- `reference-data.spec.ts`'s pre-existing timing flake under parallel Playwright workers
  (reported in PHASE_6_REPORT.md, unrelated to Phase 7, confirmed pre-existing again this phase)
  remains unfixed.
- No duplicate-analysis-request lock (see "Deviations" - explicitly permitted by the brief for
  MVP).

## Phase 8 Readiness

**READY.**

`Recommendation` rows are a stable, ownership-checked, immutable, source-backed contract Phase 8
(user cases/checklists) can reference by id without ever needing to mutate recommendation
history - exactly the boundary ADR-010 establishes. No `UserCase`, document checklist, progress
tracking, payment, or "Start this pathway" action was implemented anywhere in this phase (brief
§135/§136/§137's explicit exclusions, confirmed absent by inspection); the frontend deliberately
has no such call-to-action yet, per the brief's own recommendation.
