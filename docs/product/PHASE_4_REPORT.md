# Phase 4 Completion Report — Procedure + Versioned Legal Content Engine

Date: 2026-09-02

## Architecture

```
ProcedureCategory ──< Procedure ──< ProcedureVersion (DRAFT→IN_REVIEW→APPROVED→PUBLISHED→ARCHIVED)
                                        │  ├──< StepVersion >── ProcedureStep (identity)
                                        │  ├──< DocumentRequirementVersion >── DocumentRequirement (identity)
                                        │  │        └── references DocumentType (reusable, seeded)
                                        │  ├──< FeeVersion >── Fee (identity; scoped to this ProcedureVersion)
                                        │  └──< [5 join tables] >── OfficialSource ──< SourceVerification
                                        │             (PRIMARY / SUPPORTING / OPERATIONAL role per join)
                                        └──< ProcedureVersionOffice >── Office   (which office, per version)

Procedure ──< ProcedureAuthority >── Authority   (role-tagged: e.g. DECIDING_AUTHORITY)

Threshold ──< ThresholdVersion (independent DRAFT→…→PUBLISHED→ARCHIVED lifecycle,
              own EXCLUDE constraint — schema/service complete, zero seeded rows)
```

`Procedure` is the stable identity (code, category, jurisdiction scope); `ProcedureVersion`
is the versioned content snapshot. `ProcedureStep`/`DocumentRequirement` follow the same
identity+version split (ADR-007) so a step or requirement keeps a stable id across
versions while its content is re-snapshotted per version — no version silently inherits
another version's steps.

**Design choice — Fee vs. Threshold** (brief §16 asked this be justified): both got their
own identity+version pair, but they don't share a lifecycle. `Fee`/`FeeVersion` is scoped
to one `ProcedureVersion` — a fee only means something in the context of the procedure
version that quotes it — so it publishes and expires with that version, with no
independent Active-Version resolution of its own. `Threshold`/`ThresholdVersion` is
standalone and independently publishable (its own `PublicationStateMachine`-driven
service, its own EXCLUDE constraint), because a numeric threshold like a minimum salary
is referenced by name across procedures/rules and evolves on its own legal timeline,
unconnected to any one procedure's publish cycle. Folding Fee into a second
independently-versioned entity would have added a second Active-Version Predicate call
site and a second set of publish-lifecycle edge cases with no real evolution benefit in
this dataset.

`condition_rule_id` (sketched in the original brief for `DocumentRequirement`) was
deliberately omitted — a nullable FK to a not-yet-existing `Rule` table is exactly the
speculative-FK pattern the brief itself says to avoid. It will be added when Phase 6
creates `Rule`.

## Publication Workflow

`PublicationStateMachine` is the one authoritative transition table, shared by
`ProcedureVersion` and `ThresholdVersion`:

```
DRAFT ──submit──> IN_REVIEW ──approve──> APPROVED ──publish──> PUBLISHED ──archive──> ARCHIVED
  ^                   │
  └──── send back ────┘
```

No arbitrary skips (DRAFT can't jump straight to PUBLISHED); ARCHIVED is terminal.
Publish-readiness validation (in `ProcedurePublishingService`/`ThresholdService`, not
a single cross-entity service — see Deviations) checks: at least one `OfficialSource` is
attached, `effectiveFrom` is set and sane, and publishing won't create an overlapping
published date range for the same procedure/threshold. Failing any check returns a
typed `ApiException` (e.g. `MISSING_SOURCE`, `OVERLAPPING_PUBLISHED_VERSION`), never a
generic 500.

Roles gating the internal API: `CONTENT_EDITOR` (create/edit drafts, submit for review),
`LEGAL_REVIEWER` (approve, verify sources), `ADMIN` (publish, archive, and everything
below). All three were seeded via V33 with zero grants to any existing user — no
self-escalation.

## Temporal Resolution

The Active-Version Predicate (DATABASE.md §0) is applied exactly once per entity family,
never re-implemented ad hoc:

```sql
status = 'PUBLISHED'
AND effective_from <= :evaluationDate
AND (effective_to IS NULL OR effective_to > :evaluationDate)
```

`effective_to` is **exclusive** — a version closed on 2026-06-01 stops applying exactly
on 2026-06-01, the moment the next version's `effective_from` opens. This matches
Postgres's own half-open `[)` daterange semantics, which is what makes the
`EXCLUDE USING gist (procedure_id WITH =, daterange(effective_from, effective_to) WITH &&)
WHERE (status = 'PUBLISHED')` constraint (requires `btree_gist`) reject overlapping
published ranges at the database level, not just in application code. This is a
restatement of the existing DATABASE.md §0 convention, not a second one — the brief's
own §9 pseudocode used an inclusive comparison, which I treated as informal shorthand
rather than a second, competing spec.

Verified by test (`ProcedureVersionRepositoryTest`, `ThresholdVersionRepositoryTest`,
`ProcedureVersioningIntegrationTest`): a `DRAFT` version is never returned regardless of
dates; a `PUBLISHED` version with a future `effective_from` is not returned until that
date arrives; publishing a new version correctly closes the prior version's
`effective_to` so the two never overlap for any caller.

## Database

**14 new migrations, V20–V34** (all additive, no edits to V1–V19):

| Migration | Adds |
|---|---|
| V20–V21 | `procedure_categories` + 11 seeded categories |
| V22–V23 | `procedures` (jurisdiction_scope CHECK) + 8 MVP procedure identities |
| V24 | `official_sources`, `source_verifications` (`source_url` CHECK regex) |
| V25 | `procedure_versions` + `btree_gist` extension + EXCLUDE constraint + optimistic-lock column |
| V26 | `document_types` + 9 seeded codes |
| V27 | `procedure_steps` + `step_versions` |
| V28 | `document_requirements` + `document_requirement_versions` |
| V29 | `fees` + `fee_versions` |
| V30 | `thresholds` + `threshold_versions` + own EXCLUDE constraint |
| V31 | 5 version↔source join tables (procedure/step/document-requirement/fee/threshold) |
| V32 | `procedure_authorities` (role-tagged), `procedure_version_offices` |
| V33 | `CONTENT_EDITOR`/`LEGAL_REVIEWER`/`ADMIN` roles, no grants |
| V34 | DRAFT `ProcedureVersion` + real `OfficialSource` rows for 7 of 8 MVP procedures |

Two mid-session fixes to uncommitted migrations before they ever shipped: `fee_versions
.currency` and `threshold_versions.currency` were changed from `CHAR(3)` to `VARCHAR(3)`
— the same Hibernate schema-validation mismatch class of bug hit in Phase 3's V7.

## Seed Data

**REAL VERIFIED / sourced but not yet published** — DRAFT `ProcedureVersion` rows for
7 of the 8 MVP procedures (PESEL, Meldunek, EU Citizen Residence Registration, Temporary
Residence and Work, Temporary Residence for Studies, Family Reunification, Foreign
Driving Licence Exchange), each with real `OfficialSource` rows whose URLs come from
PROCEDURE_CATALOGUE.md's own research (UDSC/MOS/gov.pl/Mazowieckie Voivodeship Office
domains). **EU_BLUE_CARD is deliberately excluded from V34** — its only recorded source
in the catalogue is a law firm (dudkowiak.com), which CLAUDE.md and the source-type
vocabulary explicitly disqualify as a citable source; its `Procedure` identity row
exists (V23) but it has zero versions until a real official source is found.

**DRAFT (no legal verification claimed)**: none beyond the above — no numeric
`Threshold` values were seeded at all (0 rows), since no verified figure exists yet in
the catalogue and the brief explicitly said not to invent one.

**TEST-ONLY**: none left in the persistent dev database — the two `TEST_PROCEDURE_CURL_*`
rows created during my own manual curl-based verification earlier in this session were
cleared by the `docker compose down -v` done for the final regression run.

Nothing seeded here is currently PUBLISHED, by design — the public list endpoint
correctly returns `[]` against this seed data (confirmed live, see Verification below).

## APIs

**Public** (`/api/v1/procedures/**`, no auth, resolves only currently-published content):
- `GET /api/v1/procedures` — list
- `GET /api/v1/procedures/{code}` — detail (steps, document requirements, fees, sources,
  offices), 404 `PROCEDURE_NOT_FOUND` for an unknown code or a code with no published
  version

**Internal** (`/api/v1/internal/content/**`, role-gated, minimal — not the Phase 9 admin
UI): draft creation/edit, submit-for-review, approve/send-back, publish, archive, source
verification. Exact role gates:

```
POST .../versions/*/publish   → ADMIN
POST .../versions/*/archive   → ADMIN
POST .../versions/*/approve   → LEGAL_REVIEWER, ADMIN
POST .../sources/*/verify     → LEGAL_REVIEWER, ADMIN
everything else under .../internal/content/**  → CONTENT_EDITOR, ADMIN
```

## Security

Three new roles (V33), zero auto-grants. `SecurityConfig`'s most-specific-first ordering
was extended per the table above; `/api/v1/procedures/**` GETs joined the existing
public-endpoint allowlist. 401-vs-403-vs-404 conventions from CLAUDE.md were followed
throughout (verified live — see Verification).

## Frontend

- `core/services/procedure.service.ts` — typed client (`ProcedureSummary`,
  `ProcedureDetail`, `StepInfo`, `DocumentRequirementInfo`, `FeeInfo`, `SourceInfo`, etc.)
- `features/procedures/procedure-list` — "Browse procedures" page, lazy-routed at
  `/procedures`
- `features/procedures/procedure-detail` — detail page, lazy-routed at
  `/procedures/:code`
- Shell nav gained a "Browse procedures" link
- No hardcoded procedure content anywhere in these components — every field renders
  from the API response

## Source Provenance — one complete traceability example

`GET /api/v1/procedures/PESEL` → 404 today (no published version), but its DRAFT
version's provenance chain, walkable end to end in the database:

```
Procedure (code=PESEL, category=Administrative, jurisdiction_scope=NATIONAL)
  └─ ProcedureVersion (status=DRAFT, version_number=1)
       └─ procedure_version_sources (role=PRIMARY)
            └─ OfficialSource (title references the PESEL registration procedure,
                                source_url on a gov.pl / UDSC domain per
                                PROCEDURE_CATALOGUE.md's own sourcing research)
                 └─ source_verifications: none yet (status stays DRAFT until a
                                            LEGAL_REVIEWER records a VERIFIED
                                            SourceVerification — this is exactly
                                            why it hasn't been published)
```

This is the mechanism, exercised for real: query 8 in the Database Quality section below
confirms zero `PUBLISHED` version anywhere in the database cites a source lacking a
`VERIFIED` `SourceVerification` — the policy is enforced, not just documented.

## Tests

| Suite | Baseline | New | Total | Result |
|---|---|---|---|---|
| Backend (`mvnw verify`) | 112 | 40 | **152** | **152 passed, 0 failed** |
| Frontend (`npm test`) | 40 | 10 | **50** | **50 passed, 0 failed** |
| Playwright (`npm run e2e`) | 7 | 1 | **8** (7 pass + 1 pre-existing conditional skip) | **7 passed, 1 skipped (unrelated to Phase 4), 0 failed** |

The old 112/40/7 baseline is fully covered — nothing in Phases 1–3 regressed. New
backend tests: `PublicationStateMachineTest` (7), `ProcedureVersionRepositoryTest` (7),
`ThresholdVersionRepositoryTest` (5), `ProcedurePublishingServiceTest` (7),
`ProcedureApiIntegrationTest` (4), `ProcedureAdminApiSecurityTest` (9),
`ProcedureVersioningIntegrationTest` (1 — full DRAFT→…→PUBLISHED lifecycle, the most
important test in this phase). New frontend tests: `procedure.service.spec.ts` (2),
`procedure-list.spec.ts` (4), `procedure-detail.spec.ts` (4). New Playwright test:
`reference-content.spec.ts` (1, full content-API-to-public-page lifecycle).

## Bugs Found

All found through real execution (Testcontainers Postgres, real HTTP calls, a real
Playwright browser), not inferred:

1. **Hibernate CHAR/VARCHAR schema mismatch** on `fee_versions.currency` and
   `threshold_versions.currency` (same class of bug as Phase 3's V7) — fixed before
   these migrations ever shipped.
2. **LazyInitializationException** reading `ProcedureVersion.procedure` and
   `Procedure.category` outside their loading transaction — fixed with `JOIN FETCH`
   repository methods.
3. **Detached-entity mutation silently lost** — the most significant bug this phase:
   passing an entity loaded in one `@Transactional` method into a second, separate
   `@Transactional` method silently dropped the second method's write, because the
   entity wasn't managed by the second method's persistence context. Surfaced as
   "cannot transition DRAFT→APPROVED" immediately after a reported-successful submit.
   Fixed by passing `UUID` ids across service method boundaries and re-fetching inside
   each transaction.
4. **EXCLUDE-constraint flush-ordering**: publishing a new version that should close an
   old one failed with a spurious exclusion-constraint violation because Hibernate's
   automatic flush order didn't guarantee the close reached the DB first. Fixed with an
   explicit `saveAndFlush()` on the closing write.
5. **Frontend test — unflushed HTTP request** in `ProcedureList`'s loading-state test
   (same pattern as a Phase 3 bug); fixed by flushing the in-flight request.
6. **Stale backend process** reproducing already-fixed bugs during manual verification
   and again during this session's final regression pass (see Verification) — fixed by
   killing the stale process and starting fresh from the just-verified build each time.
7. **Playwright locator ambiguity** from my own earlier manual curl-based testing
   leaving duplicate-named test procedures in the dev DB — fixed by giving the
   Playwright test's own procedure a unique name and resetting the dev DB.

## Verification

Commands actually executed this session, in order, against the fully rebuilt Phase 4
code:

```
cd backend && ./mvnw verify                     → BUILD SUCCESS, jar packaged, 152/152 tests
cd frontend && npm run lint                     → All files pass linting
cd frontend && npm test -- --no-watch           → 14 files, 50/50 tests passed
cd frontend && npm run build                    → production bundle built, incl. procedure-list/detail chunks
docker compose down -v && docker compose up -d  → clean Postgres 18 + Mailpit
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run  → started, /actuator/health UP
```

Live HTTP checks against that fresh instance:

```
GET /api/v1/procedures                          → 200, []            (correct: nothing published)
GET /api/v1/procedures/PESEL                    → 404                (correct: DRAFT-only)
GET /api/v1/procedures/NOPE                     → 404                (correct: unknown code)
GET /api/v1/internal/content/procedures (no auth) → 401              (correct: unauthenticated)
```

```
npm start (Angular dev server) → 200 on localhost:4200
npm run e2e                    → 8 tests, 7 passed, 1 skipped (pre-existing, unrelated), 0 failed
```

One real environment issue hit and resolved along the way: the backend initially failed
to start with `password authentication failed for user "root"` — the documented
LOCAL_SETUP.md gotcha where pre-existing OS `DB_USERNAME`/`DB_PASSWORD` env vars shadow
the `.env` local-profile defaults. Fixed by exporting the correct values explicitly
before starting. A separate, unrelated transient issue (`npm test` failing twice with
Vitest worker crashes / `EBUSY` on a file under `node_modules`) was traced to OneDrive
file-sync contention on this OneDrive-synced working directory, not a code defect —
confirmed by a clean 50/50 pass once retried after sync settled.

## Database Quality

Ten direct SQL checks run against the freshly migrated database, all clean:

| # | Check | Result |
|---|---|---|
| 1 | Duplicate procedure codes | 0 |
| 2 | Duplicate category codes | 0 |
| 3 | Invalid `procedure_versions` date ranges (`effective_to <= effective_from`) | 0 |
| 4 | Invalid `threshold_versions` date ranges | 0 |
| 5 | Overlapping PUBLISHED `procedure_versions` per procedure | 0 |
| 6 | Orphaned `procedure_version_sources` rows | 0 |
| 7 | PUBLISHED versions with zero attached sources | 0 |
| 8 | PUBLISHED versions citing a source with no VERIFIED `SourceVerification` | 0 |
| 9 | Roles present | ADMIN, CONTENT_EDITOR, LEGAL_REVIEWER, USER |
| 10 | Row counts | procedures=8, procedure_versions=7, categories=11, document_types=9, official_sources=9, thresholds=0, threshold_versions=0, fees=0, fee_versions=0 |

## Deviations

Documented inline in IMPLEMENTATION_PLAN.md under each affected task (4.1–4.10); summary:

- 11 categories seeded, not the task list's illustrative 8-item sketch.
- `condition_rule_id` omitted from `DocumentRequirement` (speculative-FK avoidance, per
  the brief's own instruction).
- `DocumentType` added as a new reusable identity entity — not in the original sketch.
- `Fee`/`FeeVersion` is its own identity+version pair (per Phase 0's suggested
  independent design) but is scoped to one `ProcedureVersion` rather than independently
  publishable like `Threshold` — see Architecture above for the reasoning.
- `Threshold`/`ThresholdVersion` is schema-and-service complete but seeded with zero
  rows (no verified numeric figure exists yet) and not exposed via a dedicated HTTP
  endpoint in Phase 4 (no consumer needs it before Phase 6/7).
- No single cross-entity `PublishValidationService`; validation lives in
  `ProcedurePublishingService`/`ThresholdService` — revisit if a third independently
  versioned entity (e.g. `Rule` in Phase 6) needs the same lifecycle.
- `ProcedureOffice` (deferred from Phase 3 task 3.9) was implemented as
  `ProcedureVersionOffice`, scoped to the version rather than the bare procedure, since
  the office handling a procedure can change between versions.
- The internal content-management API (`ProcedureAdminController`) was necessary to
  exercise the publish lifecycle at all in Phase 4, but is intentionally minimal — not
  the full Phase 9 admin UI/API.

## Known Issues

- `Threshold` has no numeric data and no HTTP surface yet — expected to be populated and
  wired up starting in Phase 6/7 once a verified figure and a consumer exist.
- `EU_BLUE_CARD` has a `Procedure` identity but zero versions, pending a real official
  (non-law-firm) source.
- The internal content API has no dedicated admin UI (Phase 9); it was driven via direct
  HTTP calls and Playwright's `page.request` for this phase's testing.
- This OneDrive-synced working directory can intermittently make `npm test` fail with
  Vitest worker crashes / `EBUSY` under transient file-sync contention — not a code
  defect, but worth knowing: retry once if it happens.

## Phase 5 Readiness: READY

All Phase 4 code, migrations, tests, and documentation are complete and verified against
a clean rebuild. The Active-Version Predicate — the piece every later phase depends on —
is implemented once, tested for the future-dated and closed-range edge cases, and backed
by a database-level exclusion constraint. Per the user's explicit instruction, Phase 5
(questionnaire engine) is **not** started here.
