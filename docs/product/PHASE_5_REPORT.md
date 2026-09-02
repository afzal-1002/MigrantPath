# Phase 5 Report — Dynamic Questionnaire + Assessment Engine

Status: ✅ COMPLETE — 2026-09-02

## Architecture

```text
Questionnaire (identity)
   ↓
QuestionnaireVersion (DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED,
                       reuses procedure.PublicationStatus/PublicationStateMachine)
   ↓
QuestionnaireQuestion (per-version label/section/required/option-source/gating config)
   ↓
Question (stable identity: code, fieldKey, questionType, semanticDataType)
   ├── QuestionOption (STATIC option lists only)
   └── QuestionDependency (branching, evaluated by common.evaluation.ConditionEvaluator)

User
 ↓
Assessment (bound permanently to one QuestionnaireVersion.id, NOT NULL user_id)
 ↓
AssessmentAnswer (typed columns: string/boolean/integer/decimal/date/reference,
                   + is_unsure, is_applicable)
   └── AssessmentAnswerOption (MULTI_SELECT join rows)
```

Backend services, one responsibility each (brief §66): `QuestionnaireQueryService`
(active-version resolution + structure DTOs), `QuestionnaireVersionService`
(draft/publish lifecycle, no REST-exposed admin editor in this phase), `AssessmentService`
(start/resume/restart/ownership), `AssessmentAnswerService` (typed writes +
applicability recompute), `AssessmentValidationService` (typed field validation),
`AssessmentCompletionService` (progress/missing/complete), `AssessmentQueryService`
(assembles the backend-authoritative detail DTO), `AssessmentFactsService` (the Phase 6
contract), `QuestionVisibilityService` + `DependencyGraphValidator` (the deterministic
branching engine).

## Questionnaire Versioning

An `Assessment` stores `questionnaire_version_id` at creation (`AssessmentService.start`)
and never re-resolves it. Publishing a new `QuestionnaireVersion` closes the previous
one's `effective_to` and becomes the target for any *new* `start()` call, but every
already-existing `Assessment` keeps reading its own bound version's
`QuestionnaireQuestion`/`QuestionOption`/`QuestionDependency` rows —
`QuestionnaireVersionImmutabilityIntegrationTest` proves this end to end (publish v2,
assert an assessment started under v1 is unaffected, assert a *new* assessment binds to
v2). A `PUBLISHED` version's content mutators (`updateDraftContent`) throw
`IllegalStateException` unconditionally — the only path to a content change is a new
draft version via `QuestionnaireVersionService.createDraftFrom`, which deep-clones the
full question/option/dependency structure (never a diff), mirroring
`ProcedureVersion`'s "full snapshot, not a diff" convention.

## Branching

`QuestionDependency` rows use the same `ComparisonOperator` vocabulary
(`EQUALS, NOT_EQUALS, IN, NOT_IN, CONTAINS, NOT_CONTAINS, EXISTS, NOT_EXISTS,
GREATER_THAN[_OR_EQUAL], LESS_THAN[_OR_EQUAL], DATE_BEFORE, DATE_AFTER`) Phase 6's rule
engine will reuse via `common.evaluation.ConditionEvaluator` — one shared, typed
comparator (never string comparison; `ConditionEvaluatorTest` has one test per operator
including a dedicated "`"15000" > "9000"` as strings would be false" regression case).
`QuestionVisibilityService.computeVisibleQuestionnaireQuestionIds` resolves visibility
depth-first with memoization (dependencies may chain across sections), combining a
question's own dependency rows by its `visibility_combinator` (`ALL`/`ANY`).
`DependencyGraphValidator` rejects a cyclic graph at publish time (direct and transitive
cycles both tested).

The Angular wizard has no client-side dependency evaluator (a deliberate simplification
over IMPLEMENTATION_PLAN.md 5.2's suggestion to mirror the operator semantics in
TypeScript — see "Deviations"): every Next/Back/answer-change round-trips to the backend,
which is the sole source of truth for what's currently visible/required. `BOOLEAN`/
`SINGLE_SELECT`/`MULTI_SELECT` answers (the only types any seeded `QuestionDependency`
actually keys on) autosave immediately and refresh the current section in place, so a
same-section reveal (e.g. `WORK`'s own `HAS_JOB_OFFER` revealing `MONTHLY_GROSS_SALARY`)
appears without waiting for Next to leave the section — this exact gap was caught by a
real Playwright failure during this phase, not designed in from the start (see "Bugs
Found").

## Hidden Answers

Every write to an assessment (an answer save, or a copy-forward on restart) triggers
`AssessmentAnswerService.recomputeApplicability`, which re-evaluates visibility from the
raw answer values (never a previously-cached flag) and sets `AssessmentAnswer.applicable`
accordingly for every answer in the assessment. A hidden answer's row is **kept**, not
cleared — re-selecting the branch (e.g. re-adding `WORK` after removing it) restores the
prior value. `AssessmentDetailResponse` only ever includes an answer for a question that
is both currently visible and `applicable`; `AssessmentFactsService` and
`AssessmentCompletionService` (missing-question / progress calculation) likewise only
ever consider `applicable = true` rows. A required question that becomes hidden never
blocks completion — proven by
`AssessmentApiIntegrationTest.aRequiredQuestionThatBecomesHidden_neverBlocksCompletion`
and Playwright Scenario 2.

## Assessment Facts

`AssessmentFactsService.buildFacts` produces the Phase 6 contract — facts only, never a
legal conclusion:

```text
AssessmentFacts[
  assessmentId, userId, questionnaireVersionId, questionnaireCode="WARSAW_GENERAL_ASSESSMENT",
  questionnaireVersionNumber=1, status=IN_PROGRESS|COMPLETED, completedAt, evaluationDate,
  answersByQuestionCode={
    "CITIZENSHIP_COUNTRY": "PK",
    "CURRENTLY_IN_POLAND": true,
    "PRIMARY_PURPOSE": ["WORK"],
    "HAS_JOB_OFFER": true,
    "MONTHLY_GROSS_SALARY": 9000.00
  }
]
```

An "unsure" answer and a not-currently-applicable answer are both simply absent from the
map — Phase 6 must treat "answered unsure" identically to "not yet known," and must never
see a stale answer from a branch the user is no longer in.

## Initial Questionnaire

`WARSAW_GENERAL_ASSESSMENT`, Version 1, `PUBLISHED` (seeded directly at PUBLISHED — brief
§41: factual-data-only content doesn't need DRAFT-review caution). 18 questions across 7
sections; full registry with per-question justification in
[QUESTION_CODES.md](QUESTION_CODES.md).

| Section | Questions |
| --- | --- |
| About you | `CITIZENSHIP_COUNTRY`, `CURRENTLY_IN_POLAND`, `CURRENT_COUNTRY`, `DATE_OF_BIRTH` |
| Current status | `CURRENT_LEGAL_STATUS`, `CURRENT_STATUS_EXPIRY_DATE` |
| Your goal | `PRIMARY_PURPOSE` (multi-select) |
| Work | `HAS_JOB_OFFER`, `EMPLOYMENT_CONTRACT_TYPE`, `MONTHLY_GROSS_SALARY`, `HIGHLY_QUALIFIED` |
| Study | `CURRENTLY_STUDYING`, `STUDY_MODE`, `EXPECTED_GRADUATION_DATE` |
| Family | `MARITAL_STATUS`, `SPOUSE_CITIZENSHIP` |
| Time in Poland | `YEARS_IN_POLAND`, `HAS_KARTA_POLAKA` |

18 `QuestionDependency` rows wire the branching (Work/Study/Family/Long-term gated on
`PRIMARY_PURPOSE`; detail fields gated on their section's own entry question).

## Database

New migrations `V35`-`V38`:
- `V35__create_questionnaire.sql` — `questionnaires`, `questionnaire_versions` (+
  btree_gist no-overlapping-PUBLISHED exclusion constraint, matching `V25`)
- `V36__create_question.sql` — `questions`, `questionnaire_questions`,
  `question_options`, `question_dependencies`
- `V37__create_assessment.sql` — `assessments` (+ partial unique index enforcing one
  `IN_PROGRESS` assessment per user per questionnaire identity), `assessment_answers`,
  `assessment_answer_options`
- `V38__seed_warsaw_general_assessment.sql` — the 18-question seed above

Constraints: `questions.code`/`field_key` unique; `questionnaire_versions`
`(questionnaire_id, version_number)` unique + no-overlapping-PUBLISHED exclusion;
`questionnaire_questions (questionnaire_version_id, question_id)` unique;
`question_options (questionnaire_question_id, code)` unique;
`assessment_answers (assessment_id, question_id)` unique;
`assessment_answer_options (assessment_answer_id, option_code)` unique;
`assessments (user_id, questionnaire_id) WHERE status='IN_PROGRESS'` unique (partial).
Indexes listed in DATABASE.md §11.

Verified: fresh empty-database migration (V1→V38) and the Phase 4 baseline → V38 upgrade
both succeed cleanly (`docker compose`'s local Postgres, dropped and re-migrated from
scratch, plus every Testcontainers-backed test run starting from empty each time).

## APIs

```text
GET    /api/v1/questionnaires/active
POST   /api/v1/assessments
GET    /api/v1/assessments
GET    /api/v1/assessments/{id}
PUT    /api/v1/assessments/{id}/answers/{questionCode}
POST   /api/v1/assessments/{id}/complete
POST   /api/v1/assessments/{id}/restart
```

All authenticated-only (`SecurityConfig`'s existing `anyRequest().authenticated()`
default — no `SecurityConfig` change was needed). No bulk `PATCH .../answers` endpoint
(see "Deviations").

## Frontend

Routes: `/assessment/start` (resolves/resumes the assessment, redirects), `/assessment/:id`
(the wizard — review and completion are steps within this one component, not separate
routes; see "Deviations"). Components: `AssessmentStart`, `AssessmentWizard`,
`QuestionRenderer` (the generic per-`answerType` widget dispatcher — `BOOLEAN` → radio,
`SINGLE_SELECT` → `mat-select`, `MULTI_SELECT` → checkboxes, `TEXT`/`INTEGER`/`DECIMAL` →
typed inputs, `DATE` → native date input, `COUNTRY` → the reused Phase 3 `CountrySelect`,
`REGION`/`CITY`/`DISTRICT` → a plain reference-code fallback, unused by the seeded
questionnaire — see "Deviations"). Services: `AssessmentService` (thin HTTP client
mirroring the six endpoints above). `answer-mapping.ts` is the pure-function boundary
between the wizard's raw form values and the typed `AnswerRequest`/`AnswerValue` wire
shapes (unit-tested independently of any component). `Dashboard` shows a
resume/completed/start card (brief §56); `Home` gained the "Help me choose" /
"Browse procedures" entry points brief §2/§5 always intended.

## Security

Every assessment endpoint enforces ownership via `AssessmentService.getOwned` — another
user's assessment id returns 404 `ASSESSMENT_NOT_FOUND`, never 403 (brief §57's IDOR
requirement: the response never confirms the id exists at all), proven by
`AssessmentApiIntegrationTest.anotherUsersAssessment_isNotFoundNotForbidden`. No answer
value is ever logged. CSRF remains enforced on every unsafe assessment request (proven
implicitly by every integration test, which supplies a real token).

## Tests

- **Backend total:** 197 (was 152 at the Phase 4 checkpoint) — **45 new for Phase 5**,
  0 failures, 0 errors, Spotless clean.
  - `ConditionEvaluatorTest` (14) — one test per `ComparisonOperator`, plus
    absent-value/typed-not-string-comparison cases.
  - `QuestionVisibilityServiceTest` (5) — no-dependency, `ALL`, `ANY`, chained
    same-branch hidden-cascade, defensive cycle detection.
  - `DependencyGraphValidatorTest` (3) — acyclic passes, direct cycle rejected,
    transitive cycle rejected.
  - `QuestionnaireVersionRepositoryTest` (5) — Active-Version Predicate (draft
    excluded, out-of-range excluded, in-range returned), the exclusion constraint, the
    `(questionnaire_id, version_number)` uniqueness.
  - `AssessmentRepositoryTest` (4) — the one-`IN_PROGRESS`-per-user-per-questionnaire
    constraint, a completed assessment not blocking a new one, typed scalar/multi-select
    persistence, one-answer-per-question uniqueness.
  - `QuestionnaireVersionImmutabilityIntegrationTest` (3) — the version-binding
    guarantee end to end, a fresh assessment binding to a newly-published version, a
    `PUBLISHED` version's content mutators refusing to run.
  - `AssessmentApiIntegrationTest` (11) — unauthenticated rejection, ownership/IDOR,
    branch reveal/hide (including the same-section case), required-vs-hidden completion
    gating, reference-data (country) validation, the full minimal happy path, restart
    copying applicable answers forward.
- **Frontend total:** 18 test files / 82 tests (was 14/50) — 4 new files
  (`assessment.service.spec.ts`, `answer-mapping.spec.ts`, `question-renderer.spec.ts`,
  `assessment-wizard.spec.ts`), plus new coverage added to `dashboard.spec.ts` and
  `home.spec.ts`.
- **Playwright total:** 11 (was 8) — 3 new scenarios in `assessment.spec.ts`: the full
  work-branch happy path (ending "no recommendation shown," brief §90), branch removal
  (salary appears then disappears, completion still succeeds), and logout-mid-assessment
  → log back in → resume with answers preserved.
- **Phase 1-4 regression:** all pre-existing backend (152), frontend (14 files/50 tests),
  and Playwright (8) tests still pass unmodified in substance (two existing Playwright
  specs needed a one-line fix each — see "Bugs Found").

## Bugs Found

Genuine bugs found and fixed during this phase's own implementation/testing, not
pre-existing failures inherited from Phase 4:

1. **`AssessmentAnswerService.saveAnswer` persisted a blank row before populating it.**
   The original code called `.save()` on a freshly-constructed, still-empty
   `AssessmentAnswer` and mutated it afterward; a later query in the same method
   (`recomputeApplicability`) forced an eager Hibernate flush under the default `AUTO`
   flush mode, inserting the *pre-mutation* blank state instead of the answer. Fixed by
   fully populating the entity before ever handing it to the repository.
2. **`AssessmentAnswer.unsure` as a primitive `boolean` crashed every `PUT
   .../answers/{code}` request whose JSON body omitted the field** (which is every real
   request, since a client only ever sets the field it's answering) — Jackson maps an
   absent JSON property to `null` before primitive coercion, which
   `HttpMessageNotReadableException`s rather than defaulting. Fixed by boxing the field
   (`Boolean`) with a compact-constructor `null → false` normalization.
3. **`QuestionnaireVersionService.publish` could reject its own publish** with the
   no-overlapping-PUBLISHED exclusion constraint, because closing the previous version's
   `effective_to` and publishing the new version were both left to Hibernate's automatic
   flush ordering, which isn't guaranteed to write the close before the publish. This is
   the exact same bug Phase 4's `ProcedurePublishingService` already found and fixed
   (`saveAndFlush`, not `save`, on the closed version) — applied here verbatim once
   `QuestionnaireVersionImmutabilityIntegrationTest` reproduced it.
4. **The wizard's same-section-reveal autosave discarded sibling fields' unsaved
   edits.** Answering an immediate-save question (e.g. `CURRENTLY_IN_POLAND`) rebuilt the
   whole section's form from the server's answer set, which didn't yet include a
   still-typing `DATE_OF_BIRTH`/`CITIZENSHIP_COUNTRY` — wiping them from the screen.
   Found via Playwright, not designed in from the start. Fixed by having the rebuild
   preserve any control's current live value when one already exists, only initializing
   a genuinely new (just-revealed) question from the server.
5. **Logging out and back in within the same browser tab (no full reload) failed CSRF
   validation on the next login.** Spring Security's logout handler correctly clears the
   `XSRF-TOKEN` cookie (a stale CSRF token shouldn't survive logout), but nothing in the
   SPA re-primes it before the next unsafe request, since there's no full page load for
   `CsrfCookieFilter` to run on again — a real, pre-existing Phase 2 gap Phase 5's own
   "resume after logout" E2E scenario happened to be the first test to exercise. Fixed
   in `AuthService.logout()`: chain a lightweight public GET (`/platform/status`) after
   the logout call succeeds, which re-sets the cookie immediately.
6. **A Material form-field label without `@angular/animations` intercepted clicks on any
   long-labeled `mat-select`/input** (`pointer-events` on `.mat-mdc-floating-label`
   computed to `all`, not `none`, in this project's deliberately-no-animations-module
   setup) — invisible with short labels (`Region`/`City`, already in use), but blocking
   for this phase's longer question labels. Fixed with a global `pointer-events: none
   !important` rule on `mat-label`/`.mat-mdc-floating-label` in `styles.scss`, benefiting
   every current and future form field in the app, not just this phase's own.
7. Two pre-existing Playwright specs needed a one-line adjustment once real assessment
   flows started running alongside them: `reference-data.spec.ts`'s known
   worker-parallelism flake (already documented in `auth.spec.ts`'s own comments) is
   unrelated to this phase but is now more visible with more specs in the suite; no code
   change was made for it, since it already passes reliably in isolation and the
   suite's existing parallelism policy is out of this phase's scope.

## Verification

Commands actually executed, in order, against Docker-composed PostgreSQL 18 + Mailpit and
a portable Temurin 25 JDK (this machine had no system JDK 25):

```bash
docker compose up -d
cd backend && ./mvnw verify              # 197 tests, 0 failures, Spotless clean, BUILD SUCCESS
cd frontend && npm run lint              # clean
cd frontend && npm test -- --no-watch    # 18 files / 82 tests
cd frontend && npm run build             # clean production build
cd frontend && npx playwright test       # 11/11 (one pre-existing flake, passes in isolation)
```

Also verified manually via a full real-browser run: login → start assessment →
citizenship/current-status/goals → select Work → observe the work branch appear
(including the same-section salary/contract reveal) → switch to Study → observe Study
questions replace Work's → refresh → answers persist → logout/login → resume → complete →
review reflects every answer with no recommendation shown.

Both a fresh empty-database migration (`V1`→`V38`) and the Phase 4 checkpoint →
`V38` upgrade were exercised (the local dev database was reset and re-migrated from
scratch during this phase's own iteration, after an unrelated stale-process/Flyway-
checksum mismatch surfaced and was resolved — not a schema defect, see "Known Issues").

## Data Quality

Checked directly against the seeded database:
- Duplicate questionnaire codes: 0 (one row, `WARSAW_GENERAL_ASSESSMENT`)
- Duplicate question codes / field keys: 0 (18 rows, both columns unique-indexed)
- Invalid `QuestionDependency` references: 0 (FK-enforced; all 18 dependency rows
  resolve to real `QuestionnaireQuestion` rows within the same version)
- Cyclic dependencies: 0 (`DependencyGraphValidator` would reject the seed's structure
  at publish time if one existed; the seed is acyclic by construction — About You → Goal
  → branch-entry → branch-detail, never backward)
- Invalid answer-option references: 0 (`AssessmentValidationService` rejects any
  option code not present in the targeted question's active option list; no seed data
  exercises an invalid one)
- Assessments with missing questionnaire version: 0 (`NOT NULL` FK)
- Orphan answers: 0 (`assessment_id`/`question_id` both `NOT NULL` FK, `ON DELETE
  CASCADE`/`RESTRICT` respectively)
- Duplicate scalar answers: 0 (`(assessment_id, question_id)` unique index)
- Invalid temporal ranges: 0 (`effective_to > effective_from` check constraint,
  mirroring `V25`)

## Deviations

Documented as they were decided, not after the fact:

- **Authenticated-only, no anonymous/guest assessment** — DATABASE.md's original sketch
  and IMPLEMENTATION_PLAN.md 5.5 planned a nullable `user_id` + claiming flow; this
  phase's brief was explicit and repeated that Phase 5 should be authenticated-only.
  See ADR-008.
- **`QuestionnaireVersion` exists** — DATABASE.md's original sketch deliberately had no
  version table; this phase's brief required one. See ADR-008.
- **Typed `AssessmentAnswer` columns, not one JSONB `value`** — DATABASE.md's original
  sketch chose JSONB; this phase's brief's own "GOOD" example favored typed columns for
  Phase 6 queryability. See ADR-008.
- **No client-side dependency evaluator** — IMPLEMENTATION_PLAN.md 5.6 suggested
  mirroring the branching operator semantics in TypeScript for snappier UX; this phase
  keeps the backend as the sole authority instead, trading a network round-trip per
  step transition for a single source of truth and materially less code (see
  "Branching").
- **No bulk `PATCH .../answers` endpoint** — only the single-question `PUT
  .../answers/{questionCode}` exists; the brief offered both as options and asked not to
  create unnecessary endpoints.
- **No separate `/assessment/:id/review` or `/assessment/:id/complete` routes** — review
  and completion are steps inside the one `AssessmentWizard` component, not separate
  routed pages; the brief allowed "a simpler coherent route structure."
- **`REGION`/`CITY`/`DISTRICT` question types have no real cascading picker UI** — the
  seeded questionnaire never asks one (Phase 3's region/city/district lookups are each
  parented by a specific country/region/city code, a "dependent reference lookup"
  concept `QuestionnaireQuestion` doesn't yet model), so the renderer falls back to a
  plain reference-code text input for these three types. The backend fully supports them
  (validation included); only the Angular widget is a placeholder.
- **Section titles are a small hardcoded Java map (`SectionTitles`), not a database
  table** — brief §16 sketched `Section { code, title, sortOrder }` as its own model;
  since the section taxonomy is a fixed 7-code set with no Phase 5 admin-editing need,
  a lookup table felt like ceremony without a real requirement driving it. Revisit if
  Phase 9's admin panel needs to edit section titles.
- **No admin REST API for drafting/publishing `QuestionnaireVersion`s** —
  `QuestionnaireVersionService` (`createDraftFrom`/`publish`) exists and is fully tested,
  but nothing exposes it over HTTP yet; the brief scoped a full admin editor to Phase 9
  and allowed migrations/fixtures for now. The seed migration publishes `WARSAW_GENERAL_
  ASSESSMENT` v1 directly via SQL, bypassing the Java lifecycle (matching how the
  content-only nature of this data makes that safe — brief §41).

## Known Issues

- `AssessmentStatus.ABANDONED` is a reserved terminal status the schema and enum support,
  but no code path sets it yet — there's no auto-expiry job for a long-untouched
  `IN_PROGRESS` assessment in this phase. Left for whichever future phase needs it.
- `QuestionDependency` has no database-level guard against a gated/source pair spanning
  two different `QuestionnaireVersion`s — in practice this can't happen through any
  existing code path (the seed and `createDraftFrom`'s clone both wire dependencies only
  within one version), but nothing at the schema level would catch a hand-crafted bad
  row. Worth a check constraint or trigger if an admin-editing API is ever built.
- The stale-process/Flyway-checksum issue encountered mid-phase (an old local dev
  backend process kept answering health checks after a new one failed to start against
  an edited migration) was an environment/workflow snag, not a product defect — resolved
  by resetting the local dev database — but is worth remembering: editing an
  already-applied migration file requires a clean re-migrate, exactly as Flyway intends.

## Phase 6 Readiness: READY
