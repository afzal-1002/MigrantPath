# Phase 6 Report — Versioned Deterministic Rules Engine

Status: ✅ COMPLETE — 2026-09-03

## Architecture

```text
Assessment
   ↓
AssessmentFacts (Phase 5 contract: answersByQuestionCode, evaluationDate, ...)
   ↓
RuleEvaluationService
   ├── RuleRepository / RuleVersionRepository  (Active-Version Predicate lookup)
   └── RuleEvaluator
         ├── ConditionTreeParser        (raw JSONB → sealed ConditionNode tree)
         ├── FactResolver               (direct facts from AssessmentFacts;
         │                                derived: AGE_YEARS, IS_OUTSIDE_EU_EEA_SWISS_
         │                                FREE_MOVEMENT_GROUP, COUNTRY_GROUP_MEMBERSHIPS)
         ├── ThresholdService.findActiveVersion   (Active-Version Predicate for Threshold)
         ├── CountryClassificationService.isMember (IS_MEMBER_OF_COUNTRY_GROUP leaves)
         └── ConditionEvaluator          (Phase 5's typed comparator, reused as-is
                                           + BETWEEN/DATE_*_OR_EQUAL added)
                  ↓
         RuleEvaluationResult (per rule) → RuleEvaluationBundle (per assessment,
                                            grouped by targetCode)
```

`ConditionTreeValidator` sits outside the runtime evaluation path — it runs once, in
`RulePublishingService`, before a `RuleVersion` may reach `PUBLISHED`, so the runtime
`RuleEvaluator` above almost never encounters a genuinely broken rule (and treats it as
`ERROR`, never a crash, when it does).

Package layout: `com.foreignerwarsaw.rules.core` (identity/lifecycle),
`com.foreignerwarsaw.rules.condition` (parser/validator), `com.foreignerwarsaw.rules.evaluation`
(the evaluator + Phase 7 output contract) — three focused packages, not one giant
`RulesEngineService` (brief §41).

## Database

Three new migrations, sequential from the existing baseline (V38 was the last Phase 5
migration):

- **V39 `create_rule.sql`** — `rules` (identity: code, canonical_name, rule_type,
  target_type, target_code, jurisdiction_id nullable, active), `rule_versions` (identity+
  version+lifecycle, `condition_tree JSONB NOT NULL`, `condition_schema_version`,
  `explanation_key`, `change_summary`, actor/timestamp columns, `lock_version`, the
  `btree_gist` `rule_versions_no_overlapping_published` exclusion constraint), `rule_version_sources`
  (composite-key join to `official_sources`, `role` CHECK including the new `LEGAL_BASIS`).
- **V40 `create_rule_threshold_reference.sql`** — `rule_threshold_references`
  (`rule_version_id` FK CASCADE, `threshold_code` FK RESTRICT, composite PK, index on
  `threshold_code`).
- **V41 `create_rule_outcome.sql`** — `rule_outcomes` (placeholder, per DATABASE.md §5's
  "not required for MVP" note — no repository, no service references it).

Verified live against the real local Postgres 18 database (`\d rule_versions`, this
report's "Verification" section): the exclusion constraint, unique
`(rule_id, version_number)`, both check constraints, and every FK exist exactly as
migrated. `SourceRole` gained a fourth value, `LEGAL_BASIS` (edit to the existing enum,
not a new migration column — it's stored as a `VARCHAR` CHECK, and only
`rule_version_sources`' CHECK constraint accepts it).

`ComparisonOperator`/`ConditionEvaluator` (`com.foreignerwarsaw.common.evaluation`,
originally built for Phase 5's `QuestionDependency`) were extended, not duplicated:
`BETWEEN`, `DATE_BEFORE_OR_EQUAL`, `DATE_AFTER_OR_EQUAL`,
`IS_MEMBER_OF_COUNTRY_GROUP`/`IS_NOT_MEMBER_OF_COUNTRY_GROUP` added (the last two throw
from `ConditionEvaluator` itself if reached — `RuleEvaluator` must intercept them first).

## Rule Model

`Rule` (identity) vs `RuleVersion` (versioned content) mirrors `Procedure`/`ProcedureVersion`
and `Threshold`/`ThresholdVersion` exactly, reusing `PublicationStatus`/
`PublicationStateMachine` directly (brief §55). `Rule.targetType` is one of `PROCEDURE`,
`DOCUMENT_REQUIREMENT`, `STEP`, `FEE`, `THRESHOLD_APPLICABILITY`, `ROUTING` — **only
`PROCEDURE` is actually evaluated by `RuleEvaluationService`**; the rest are declared so a
later target needs no schema change (brief §6, and see "Deviations" for why
`DOCUMENT_REQUIREMENT`/`STEP`/`FEE` are not yet resolvable target codes). `Rule.ruleType`
is one of `ELIGIBILITY`, `APPLICABILITY`, `EXCLUSION`, `REQUIREMENT`,
`INFORMATION_REQUIRED` (brief §7) — carried on the entity, read by nothing in Phase 6's
evaluator itself (Phase 7's aggregation is where rule type starts mattering).

Publishing lifecycle: `RuleService` (identity create/lookup) →
`RuleVersionService` (draft create/update/submit/approve, `createDraftFrom` clone-forward)
→ `RulePublishingService` (validate-then-publish, exactly mirroring
`ProcedurePublishingService`'s pattern including the `saveAndFlush`-before-`markPublished`
fix for the exclusion-constraint flush-order bug). Full detail in
[RULE_PUBLICATION_POLICY.md](../rules/RULE_PUBLICATION_POLICY.md).

## Condition Schema

Full schema and examples in [RULE_SCHEMA.md](../rules/RULE_SCHEMA.md). Summary: a
`ConditionNode` (sealed interface: `AllNode`/`AnyNode`/`NotNode`/`LeafCondition`) tree,
parsed structurally by `ConditionTreeParser` (max depth 10, exactly one of
`all`/`any`/`not`/leaf per node, unknown operator/field rejected) and validated
semantically by `ConditionTreeValidator` (unknown fact/operator-for-type/threshold/
country-group rejected, every problem accumulated, not just the first). Example:

```json
{
  "all": [
    { "fact": "CITIZENSHIP_COUNTRY", "operator": "IS_NOT_MEMBER_OF_COUNTRY_GROUP", "value": "EU_MEMBER" },
    { "fact": "MONTHLY_GROSS_SALARY", "operator": "GREATER_THAN_OR_EQUAL", "threshold": "BLUE_CARD_MIN_SALARY" }
  ]
}
```

Implemented operators: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `CONTAINS`,
`NOT_CONTAINS`, `EXISTS`, `NOT_EXISTS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`,
`LESS_THAN`, `LESS_THAN_OR_EQUAL`, `BETWEEN`, `DATE_BEFORE`, `DATE_BEFORE_OR_EQUAL`,
`DATE_AFTER`, `DATE_AFTER_OR_EQUAL`, `IS_MEMBER_OF_COUNTRY_GROUP`,
`IS_NOT_MEMBER_OF_COUNTRY_GROUP`. `DURATION_*` deliberately not implemented (brief §45 —
no fact needs it yet).

## Missing Semantics

Full exhaustive table in [OPERATOR_SEMANTICS.md](../rules/OPERATOR_SEMANTICS.md). Summary:

- Leaf result: `PASS` / `FAIL` / `MISSING` (absent fact, non-`EXISTS`-family operator) /
  `ERROR` (broken configuration — unknown threshold at runtime, a type mismatch, a parse
  failure).
- `ALL`: `ERROR` > `FAIL` > `MISSING` > `PASS` (a known "no" always outranks an
  unrelated unanswered question).
- `ANY`: `PASS` > `ERROR` > `MISSING` > `FAIL` (a single known "yes" is enough).
- `NOT`: inverts `PASS`/`FAIL`; passes `MISSING`/`ERROR` through unchanged.
- Rule status: `PASS→SATISFIED`, `FAIL→NOT_SATISFIED`, `MISSING→INDETERMINATE`,
  `ERROR→ERROR`.
- An `ERROR` is never silently converted to `NOT_SATISFIED` anywhere in the engine
  (brief §64/§118) — `RuleEvaluationController`/`RuleEvaluationService` return it as-is;
  a future Phase 7 must not treat `ERROR` as a confident "no."
- An `UNSURE` Phase 5 answer is, by construction, absent from `AssessmentFacts`
  (Phase 5's own `AssessmentFactsService`), so it is indistinguishable from — and
  produces exactly the same `MISSING` result as — a question never reached.

## Fact Registry

Full table in [FACT_REGISTRY.md](../rules/FACT_REGISTRY.md). 18 direct facts (exactly
the `WARSAW_GENERAL_ASSESSMENT` seed's `Question.code`s — `FactRegistry` reads
`QuestionRepository` live, never a hand-maintained duplicate list) plus 3 derived facts:
`AGE_YEARS` (from `DATE_OF_BIRTH`), `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP` and
`COUNTRY_GROUP_MEMBERSHIPS` (both from `CITIZENSHIP_COUNTRY`, via
`CountryClassificationService`). Allowed operators per fact are derived mechanically
from `QuestionType` (`FactRegistry.operatorsFor`), never hand-curated per fact.
`THIRD_COUNTRY` as a universal boolean does not exist (ADR-006, confirmed unchanged).

## Thresholds

`Threshold`/`ThresholdVersion` already existed from Phase 4 — Phase 6 added no new
threshold infrastructure, only consumed it: `RuleEvaluator` resolves a leaf's
`threshold` reference via `ThresholdService.findActiveVersion(code, evaluationDate)`
(the Active-Version Predicate, unchanged), records the resolved `ThresholdVersion.id`/
`value`/`effectiveFrom` in a `ThresholdUsage` entry on the result (brief §51's exact
reproducibility requirement), and produces `ERROR` — never a silent pass/fail — if no
active `PUBLISHED` version exists for that date. `RuleThresholdReference` rows are
rebuilt from the condition tree on every publish (never hand-maintained), answering
"which rules depend on threshold X" as a plain indexed query. No new `Threshold` rows
were seeded (brief §21/§58 — same "no unverified legal value" discipline Phase 4 already
followed).

## Temporal Evaluation

Example, proven by `RuleEngineIntegrationTest`: a `RuleVersion` v1 published with
`effectiveFrom = today`, then a v2 published with `effectiveFrom = today + 1 month`.
Evaluating with `evaluationDate = today` resolves v1 (`ruleVersionNumber() == 1`);
evaluating with `evaluationDate = today + 1 month` resolves v2 — proving both the Active-
Version Predicate itself and the exclusion-constraint flush-order fix (publishing v2
correctly closes v1's `effective_to` before v2's own insert, under a real `btree_gist`
constraint, not a mock). `ThresholdVersion` resolution uses the identical predicate,
independently of `RuleVersion` — the two timelines are genuinely decoupled (brief §62).

## Country Classification

`RuleEvaluator` never duplicates an EU/EEA/EFTA/Schengen country list — every
`IS_MEMBER_OF_COUNTRY_GROUP`/`IS_NOT_MEMBER_OF_COUNTRY_GROUP` leaf is intercepted before
ever reaching `ConditionEvaluator` and delegated to the existing (Phase 3/ADR-006)
`CountryClassificationService.isMember(country, group, evaluationDate)`. Proven against
really-seeded reference data in `RuleEngineIntegrationTest`: Germany (`DE`) is `EU_MEMBER`
today; Pakistan (`PK`) is not. `SCHENGEN` was never referenced by any Phase 6 code or
test — it remains fully independent, never implying residence/work rights (brief §28).
No `GB`-nationality-implies-Withdrawal-Agreement-rights logic exists anywhere (brief §27)
— no fact for it exists yet; a rule needing it would correctly return `MISSING`. No
universal `THIRD_COUNTRY` boolean was reintroduced anywhere (confirmed by inspection of
every new file — the only classification facts are the two structural derived facts
above, both honestly named per ADR-006's existing discipline).

## APIs / Services

**No admin HTTP API for Rule management** (deliberate — see "Deviations" and
[RULE_PUBLICATION_POLICY.md](../rules/RULE_PUBLICATION_POLICY.md)): `RuleService` /
`RuleVersionService` / `RulePublishingService` are exercised directly by
`RuleEngineIntegrationTest`, mirroring `ThresholdService`'s Phase 4 precedent.

**One new HTTP endpoint**, authenticated + ownership-checked:

```
GET /api/v1/assessments/{id}/rule-evaluations?evaluationDate=YYYY-MM-DD (optional)
→ RuleEvaluationBundle
```

**Application-service contract for Phase 7** (`RuleEvaluationService`,
`com.foreignerwarsaw.rules.evaluation`):

- `evaluateRulesForProcedure(procedureCode, facts, evaluationDate) → List<RuleEvaluationResult>`
- `evaluateApplicableRules(facts, evaluationDate) → RuleEvaluationBundle` (every active
  rule, grouped by `targetCode`, union of every result's `missingFacts`, tagged with
  `RuleEvaluator.ENGINE_VERSION`)
- `previewEvaluate(conditionTreeJson, explanationKey, facts, evaluationDate) → RuleEvaluationResult`
  (a dry-run against raw, not-yet-persisted JSON — no `Rule`/`RuleVersion` row involved,
  result carries no rule identity, `ruleCode = "PREVIEW"`)

## Security

Rule-management roles (`CONTENT_EDITOR` create/draft, `LEGAL_REVIEWER` approve, `ADMIN`
publish/archive, `USER` none) are documented and exercised at the service layer via real
accounts with real roles in `RuleEngineIntegrationTest` (through the existing
`/api/v1/internal/content/sources` HTTP endpoints for source creation/verification, which
already enforce them). No new HTTP surface exists for the Rule/RuleVersion lifecycle
itself, so there is no controller-level role gate to test there — see "Deviations."
`GET /api/v1/assessments/{id}/rule-evaluations` reuses the exact ownership pattern every
other assessment endpoint uses (`AssessmentService#getOwned`): proven 200 for the owner,
404 (never 403) for another user's assessment id, 401 unauthenticated
(`RuleEngineIntegrationTest.ruleEvaluationEndpoint_ownAssessment_returnsBundle_anotherUsersAssessment_isNotFound`).
CSRF is unaffected (a `GET` endpoint needs none; nothing was disabled).

## Seed Data

- **REAL VERIFIED**: none. No production `Rule`/`Threshold` content was published.
- **DRAFT**: none.
- **TEST-ONLY**: synthetic `TEST_*`-coded `Rule`/`Threshold` rows created only inside
  `RuleEngineIntegrationTest` (never in a Flyway migration, never reaching a real
  environment) — confirmed empty in the real local database by the data-quality queries
  below.

An empty production Rules catalogue is the intended state (brief §60).

## Tests

**Backend total: 255 tests, 0 failures, 0 errors** (`./mvnw verify`, includes Spotless
check — clean). Phase 6 added 6 new files' worth of unit/integration tests plus extended
one existing file:

| Test class | Count | Kind |
|---|---|---|
| `ConditionEvaluatorTest` (extended) | 19 | Unit — `BETWEEN` (inclusive bounds + malformed-array), `DATE_BEFORE_OR_EQUAL`/`DATE_AFTER_OR_EQUAL`, country-group-operator guard |
| `ConditionTreeParserTest` (new) | 11 | Unit — structural parsing, malformed shapes, depth limit, unknown operator/field |
| `ConditionTreeValidatorTest` (new) | 8 | Unit (mocked repos) — unknown fact/operator-type/threshold/country-group, problem accumulation |
| `RuleEvaluatorTest` (new) | 17 | Unit (mocked collaborators) — ALL/ANY/NOT combination table, MISSING/EXISTS edge cases, threshold + country-group resolution, ERROR safety, source-id/preview envelope |
| `RulePublishingServiceTest` (new) | 10 | Unit (mocked repos) — every publish-readiness gate, `LEGAL_BASIS` vs `PRIMARY` sourcing, threshold-reference resync, archive bypasses validation |
| `RuleEvaluationServiceTest` (new) | 5 | Unit (mocked repos/evaluator) — target filtering, content-gap skipping, bundle grouping, missing-fact union, preview delegation |
| `RuleEngineIntegrationTest` (new) | 2 | Full-stack Testcontainers Postgres 18 — real create→draft→submit→approve→publish lifecycle, real threshold + real seeded country-group resolution, temporal (future-dated v2) evaluation including the exclusion-constraint race, and the evaluation-endpoint IDOR/401 test |

New Phase 6 test methods: **53** in new files, plus 6 extended into the existing
`ConditionEvaluatorTest`.

**Regression (Phase 1-5): all green**, run in the same `./mvnw verify` invocation as part
of the 255-test total — no Phase 6 change touched any Phase 1-5 file except the two
narrow, additive extensions to `ComparisonOperator`/`ConditionEvaluator` and one enum
constant on `SourceRole`.

**Frontend**: 82/82 unit tests pass (`npm test -- --no-watch`), `npm run lint` clean,
`npm run build` succeeds. No frontend files were added or changed in Phase 6 (brief
§90/§106) — this is confirmation of zero regression, not new coverage.

**Playwright**: 11/11 scenarios pass (`npm run e2e`, against the real local backend +
Postgres + Mailpit). One scenario (`reference-data.spec.ts`, unrelated to Phase 6)
timed out under 6-way parallel worker contention on the first run and passed cleanly
re-run in isolation — a pre-existing timing flake under parallel load, not a Phase 6
regression (no file that test touches was changed this phase).

## Bugs Found

None new. The exclusion-constraint flush-order bug (documented `saveAndFlush`-before-
`markPublished` requirement) was *pre-emptively applied* to `RulePublishingService` from
the start, based on the three prior occurrences (`ProcedurePublishingService`,
`ThresholdService`, `QuestionnaireVersionService`) — `RuleEngineIntegrationTest`'s
future-dated-version scenario confirms it works correctly against a real database rather
than rediscovering the bug a fourth time.

## Verification

Commands actually executed, in order:

```bash
cd backend && ./mvnw -o spotless:apply
cd backend && ./mvnw -o compile                 # after each logical batch of new files
cd backend && ./mvnw -o test-compile
cd backend && ./mvnw -o test -Dtest=<new classes>   # incremental, before the full suite
rm -rf backend/target                            # Windows file-lock on `clean`, per CLAUDE.md
cd backend && ./mvnw -o verify                    # 255 tests, 0 failures — BUILD SUCCESS
cd frontend && npm run lint                       # clean
cd frontend && npm test -- --no-watch             # 82/82 passed
cd frontend && npm run build                      # succeeded
docker compose up -d                              # Postgres 18 + Mailpit (already running)
cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw -o spring-boot:run   # real local DB, V1→V41 applied fresh
curl http://localhost:8080/actuator/health         # UP
cd frontend && npm run e2e                        # 11/11 passed (1 flaky-under-load, isolated pass confirmed)
docker exec ... psql ... <data-quality queries>    # see below
docker exec ... psql -c "\d rule_versions"         # exclusion constraint / FKs / checks confirmed present
```

## Data Quality

Run against the real local Postgres database (schema fresh through V41, no Rule/Threshold
content ever seeded there):

| Check | Result |
|---|---|
| Duplicate `rules.code` | 0 |
| Duplicate `thresholds.code` | 0 |
| Duplicate `(rule_id, version_number)` | 0 |
| Invalid temporal ranges (`effective_to <= effective_from`) on `rule_versions` | 0 |
| Published `rule_versions` without a `VERIFIED` `PRIMARY`/`LEGAL_BASIS` source | 0 |
| Orphan `rule_threshold_references` (threshold code not in `thresholds`) | 0 |
| Orphan `rule_version_sources` (official source id not in `official_sources`) | 0 |
| `rules`/`rule_versions`/`thresholds`/`threshold_versions` row counts | 0 / 0 / 0 / 0 |

All zero — expected and correct given the deliberate empty-production-catalogue policy
(no rows exist to violate anything); the queries themselves are real, run against the
real schema, and would catch a violation in real content just as readily. Structural
integrity (the exclusion constraint, unique/check constraints, every FK) was separately
confirmed present via `\d rule_versions` against the live database, not merely inferred
from the migration source.

## Performance

Not separately load-tested (no real rule volume exists to measure against yet, per the
seed-data policy above). By construction, `RuleEvaluationService.evaluateAll` issues one
`findActivePublishedVersion` query per candidate rule and delegates to one in-memory tree
walk per rule (`RuleEvaluator.evaluate`) — no query is issued per leaf condition; a
`threshold` reference issues one `ThresholdService.findActiveVersion` query and a country-
group reference issues one `CountryClassificationService` call, each per leaf that
actually uses one (no evaluation-run-scoped cache was added — brief §86 permits but does
not require one, and Phase 6's rule volume is zero in production, so it was not built
ahead of a measured need). `RuleEngineIntegrationTest`'s full lifecycle-plus-five-
evaluations scenario completed in ~6.6s including Testcontainers Postgres startup
overhead already amortized by the surrounding suite.

## Deviations

- **No admin HTTP API for Rule management** was built (brief §76 offered this as
  optional; §55 and §43's Phase-4 precedent — "if implementing all management endpoints
  is too much, defer UI/API breadth" — were followed instead). `RuleService`/
  `RuleVersionService`/`RulePublishingService` are the extension point; this mirrors
  `ThresholdService` shipping zero controller in Phase 4 for the identical reason (no
  real content yet to manage through one).
- **6.8's "Admin rule-preview endpoint"** (IMPLEMENTATION_PLAN.md) was implemented as a
  service method (`RuleEvaluationService.previewEvaluate`) rather than an HTTP endpoint,
  for the same reason as above — no admin surface exists to expose it through yet.
- **`RuleTargetType` is broader than actively evaluated**: `DOCUMENT_REQUIREMENT`/`STEP`/
  `FEE` are declared but not resolvable target codes yet, because those three entities
  are `Procedure`-scoped (their `stableCode` is unique only *within* a procedure, not
  globally — discovered while inspecting `DocumentRequirementRepository`/
  `ProcedureStepRepository`/`FeeRepository`), so a bare `Rule.targetCode` string is
  ambiguous for them. Resolving this (a composite code convention, or a
  `target_procedure_code` column) is deferred to whichever later phase actually needs a
  non-`PROCEDURE` rule target — building it now would be speculative (brief §6's own "do
  not build every future target type unless necessary").
- **`ARCHITECTURE.md §7`** previously conflated Phase 6/7 output (`PRIMARY_MATCH` etc.
  described as this section's own output) — corrected to attribute ranking/recommendation
  language explicitly to Phase 7, per this brief's own instruction.
- **A fourth `RuleEvaluationResult` list, `errorConditions`**, was added beyond the
  brief's exact §32 field list, to keep the four-state `ConditionResult` model
  (`PASS`/`FAIL`/`MISSING`/`ERROR`) fully self-consistent — every leaf's actual outcome
  lands in a correspondingly-named list, `ERROR` traces were never folded into
  `failedConditions` (which would have misrepresented a configuration problem as a
  legitimate `FAIL`).
- **`RuleOutcome`** was created exactly as DATABASE.md §5 anticipated (a placeholder,
  brief §36/§53) but is genuinely unused — no repository, no service — per brief §24's
  explicit "avoid a generic dependency graph just because it sounds powerful."

## Known Issues

- The `DOCUMENT_REQUIREMENT`/`STEP`/`FEE`/`THRESHOLD_APPLICABILITY`/`ROUTING` target
  types are declared in the schema/enum but have no resolvable-target-code convention or
  evaluation path yet (see "Deviations" above) — not a defect for Phase 6's scope
  (`PROCEDURE` only), but a real gap the first non-`PROCEDURE` rule will need to close.
- No evaluation-run-scoped cache exists (brief §86 permits, does not require, one) — with
  zero production rules this has no observable effect today; worth revisiting once real
  rule volume exists and repeated threshold/country-group lookups within one run become
  measurable.
- `reference-data.spec.ts`'s pre-existing timing flake under 6-way parallel Playwright
  workers (unrelated to Phase 6, confirmed pre-existing by isolated re-run) was not
  fixed — out of this phase's scope, noted for whoever next touches that spec or the
  Playwright worker count.

## Phase 7 Readiness

**READY.**

`RuleEvaluationService.evaluateApplicableRules(facts, evaluationDate) → RuleEvaluationBundle`
is a stable, tested, documented contract: results grouped by target code, every result
carrying its `RuleEvaluationStatus`, structured PASS/FAIL/MISSING/ERROR traces, the exact
`ThresholdVersion`s used, and `OfficialSource` ids — everything Phase 7 needs to rank
procedures and generate `PRIMARY_MATCH`/`POSSIBLE_ALTERNATIVE`/`MORE_INFORMATION_REQUIRED`
without touching a single rule/condition-tree concept itself. Phase 7 has not been
started; no ranking, categorical output, or recommendation logic exists anywhere in this
phase's code.
