# Implementation Plan — Foreigner Warsaw

Status: DRAFT (Phase 0)
Last updated: 2026-09-01

Breaks the roadmap in [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) §9 into small,
independently testable tasks. Entities referenced in Database/Backend columns are
specified in [DATABASE.md](../database/DATABASE.md); the module a task lives in is per
[ARCHITECTURE.md](../architecture/ARCHITECTURE.md) §1. Tasks are numbered `phase.task`
and are meant to be completable (and reviewable) one at a time, in order within a phase
unless a task's **Depends on** points elsewhere.

Convention used below: `—` means that column doesn't apply to the task.

---

## Phase 1 — Repository + infrastructure

#### 1.1 Scaffold backend Spring Boot project
- **Goal:** A buildable, empty Spring Boot 4.1.x / Java 25 app with the module package
  skeleton from ARCHITECTURE.md §1.
- **Backend:** `backend/` via Spring Initializr equivalent (group `com.foreignerwarsaw`,
  artifact `backend`, Java 25, jar packaging), Maven Wrapper committed, base deps (Web,
  Data JPA, Security, Validation, Actuator, PostgreSQL driver, Flyway).
- **Frontend:** —
- **Database:** —
- **Tests:** `mvnw test` runs (no tests yet, build passes).
- **DoD:** `./mvnw spring-boot:run` starts with no errors against no datasource yet
  (or a dev H2 stub) — real Postgres wiring is 1.5.
- **Depends on:** —
- **Risk:** Low

#### 1.2 Scaffold frontend Angular project
- **Goal:** A buildable, empty Angular 22 standalone app.
- **Backend:** —
- **Frontend:** `frontend/` via current Angular CLI (`--routing --style=scss`,
  standalone components, no NgModules), base `core`/`shared`/`layout`/`features` folders
  per ARCHITECTURE.md §10.
- **Database:** —
- **Tests:** `npm test` / `ng build` succeed.
- **DoD:** `npm start` serves a blank shell page.
- **Depends on:** —
- **Risk:** Low

#### 1.3 Add Angular Material
- **Goal:** UI component library wired in with a base theme.
- **Frontend:** `ng add @angular/material`, pick a placeholder theme, confirm one
  Material component renders.
- **Tests:** existing build/tests still pass.
- **DoD:** a Material button renders on the shell page.
- **Depends on:** 1.2
- **Risk:** Low

#### 1.4 Docker Compose for local PostgreSQL
- **Goal:** `docker compose up -d postgres` gives a local Postgres 18 instance.
- **Database:** `infra/docker/docker-compose.yml` (or root `docker-compose.yml` per
  ARCHITECTURE.md §3) with a `postgres:18` service, named volume, `foreigner_warsaw` DB,
  credentials from `.env`.
- **Tests:** manual — `psql` connects.
- **DoD:** container healthy, DB reachable on the configured port.
- **Depends on:** —
- **Risk:** Low

#### 1.5 Wire Spring Boot datasource + Flyway
- **Goal:** Backend connects to Postgres and Flyway is active with `ddl-auto=validate`.
- **Backend:** `application-local.yml`/`application-test.yml`, Flyway dependency
  configured, `ddl-auto=validate` in every profile (ADR-002).
- **Database:** `V1__baseline.sql` — an intentionally minimal baseline migration (no
  domain tables yet; those start in Phase 2/3) so the migration chain exists from day
  one.
- **Tests:** Spring context loads against the Compose Postgres instance.
- **DoD:** `./mvnw spring-boot:run` connects, Flyway reports one applied migration.
- **Depends on:** 1.1, 1.4
- **Risk:** Low

#### 1.6 Actuator health endpoint + structured logging baseline
- **Goal:** `/actuator/health` reachable; logs are structured with a request
  correlation ID.
- **Backend:** Actuator exposed narrowly (health/info only, per ARCHITECTURE.md §11 —
  no sensitive endpoints public), a correlation-ID filter/MDC setup.
- **Tests:** integration test hits `/actuator/health`, expects 200.
- **DoD:** health check green locally and in CI.
- **Depends on:** 1.5
- **Risk:** Low

#### 1.7 `.env.example` and environment profiles
- **Goal:** New contributors can configure local/test/staging/production without
  guessing variable names.
- **Backend/Database:** `.env.example` with `DB_HOST`, `DB_PORT`, `DB_NAME`,
  `DB_USERNAME`, `DB_PASSWORD`, `APP_BASE_URL`, `FRONTEND_URL`, `MAIL_HOST`,
  `MAIL_PORT`, `ADMIN_INITIAL_EMAIL` (no real secrets committed).
- **DoD:** a fresh clone + `.env` from the example + `docker compose up` boots cleanly.
- **Depends on:** 1.4, 1.5
- **Risk:** Low

#### 1.8 GitHub Actions CI skeleton
- **Goal:** Every PR runs backend build+test and frontend build+test.
- **Backend/Frontend:** `.github/workflows/ci.yml` — Maven build+test job, npm
  install+build+test job, running in parallel.
- **Tests:** the workflow itself, validated by opening a throwaway PR.
- **DoD:** CI is green on a trivial PR and red on an intentionally broken one.
- **Depends on:** 1.1, 1.2
- **Risk:** Low

#### 1.9 Full local Docker Compose (backend + frontend + postgres)
- **Goal:** One command runs the whole stack for anyone who doesn't want to run
  `mvnw`/`npm` directly.
- **Database/Backend/Frontend:** root `docker-compose.yml` with all three services;
  local dev docs updated with both the "compose everything" and the "compose Postgres,
  run backend/frontend natively" workflows (Product's preferred day-to-day loop).
- **DoD:** `docker compose up` serves the frontend and a working `/api/v1` health check.
- **Depends on:** 1.1–1.6
- **Risk:** Medium (multi-service Compose networking is the usual snag)

#### 1.10 Local email catcher (Mailpit)
- **Goal:** Email-dependent flows (Phase 2) are testable locally without a real SMTP
  provider.
- **Database/Backend:** Mailpit service added to Compose; Spring Mail configured to
  point at it in the local profile.
- **DoD:** a test email sent from the backend appears in Mailpit's UI.
- **Depends on:** 1.9
- **Risk:** Low

#### 1.11 Lint/format baseline
- **Goal:** Consistent style enforced in CI, not by review nitpicking.
- **Backend:** Spotless or Checkstyle wired into the Maven build.
- **Frontend:** ESLint + Prettier wired into `npm run lint`.
- **Tests:** CI fails on a deliberately misformatted file.
- **DoD:** lint job added to 1.8's workflow and passing on `main`.
- **Depends on:** 1.8
- **Risk:** Low

---

## Phase 2 — Authentication + users

#### 2.1 Migration: core user/security tables
- **Goal:** Schema for `User`, `Role`, `UserRole`, `UserProfile`,
  `EmailVerificationToken`, `PasswordResetToken`, `UserConsent` per DATABASE.md §1.
- **Database:** `V2__user_security.sql` with constraints/uniques/indexes as specified
  there (case-insensitive email uniqueness in particular).
- **Tests:** Flyway migrates cleanly in a Testcontainers-backed test.
- **DoD:** migration applies on a clean DB and on top of `V1`.
- **Depends on:** 1.5
- **Risk:** Low

#### 2.2 Seed roles
- **Goal:** `USER`/`ADMIN` exist; reserved role codes exist but are unused.
- **Database:** seed migration or `ApplicationRunner` inserting `Role` rows.
- **DoD:** both roles queryable after startup.
- **Depends on:** 2.1
- **Risk:** Low

#### 2.3 User domain + repository
- **Goal:** JPA entity/repository for `User`, mapped per DATABASE.md §1.
- **Backend:** `auth`/`user` module entities + Spring Data repositories.
- **Tests:** repository unit/integration test (save/find by email, case-insensitive).
- **DoD:** repository tests green.
- **Depends on:** 2.1
- **Risk:** Low

#### 2.4 Registration endpoint
- **Goal:** `POST /api/v1/auth/register` — email + password + ToS/Privacy acceptance
  only (Product Requirements §6.1).
- **Backend:** request DTO + validation, password hashing (Spring Security defaults),
  creates `User` + `UserConsent` rows, issues an `EmailVerificationToken`.
- **Database:** none beyond 2.1.
- **Tests:** unit test on the service; integration test asserting a 201 and a persisted
  user with `email_verified_at IS NULL`.
- **DoD:** duplicate-email registration returns a clean `VALIDATION_ERROR`
  (ARCHITECTURE.md §6 error envelope), not a raw DB constraint error.
- **Depends on:** 2.2, 2.3
- **Risk:** Low

#### 2.5 Email verification flow
- **Goal:** Verification email sent (via Mailpit locally) and clicking it verifies the
  account.
- **Backend:** `POST /api/v1/auth/verify-email` (or GET with token in path per final
  design), token hash lookup, expiry check, sets `email_verified_at`.
- **Tests:** integration test: expired token rejected, valid token verifies once and is
  not reusable.
- **DoD:** end-to-end register → check Mailpit → verify works locally.
- **Depends on:** 2.4, 1.10
- **Risk:** Medium (token expiry/replay edge cases)

#### 2.6 Login/logout with secure session cookie
- **Goal:** Cookie-based session auth per ADR-005 (no bearer token in `localStorage`).
- **Backend:** Spring Security configuration issuing HTTP-only, `Secure`-in-production,
  `SameSite`-appropriate cookies; `POST /api/v1/auth/login`, `POST
  /api/v1/auth/logout`.
- **Tests:** integration test: correct credentials → session cookie set; wrong password
  → 401; logout invalidates the session.
- **DoD:** login/logout works against a real browser session in local dev.
- **Depends on:** 2.4
- **Risk:** Medium (cookie/CORS/CSRF interplay is easy to get subtly wrong — see Phase
  12 for the full hardening pass)

#### 2.7 Forgot/reset password
- **Goal:** `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`.
- **Backend:** issues `PasswordResetToken`, emails a reset link, validates token +
  updates `password_hash` on reset, invalidates existing sessions.
- **Tests:** integration test covering the full round trip and token single-use.
- **DoD:** works end-to-end locally via Mailpit.
- **Depends on:** 2.6, 1.10
- **Risk:** Low

#### 2.8 Change password (authenticated)
- **Goal:** Logged-in user can change their password given the current one.
- **Backend:** `PUT /api/v1/users/me/password` requiring current-password
  confirmation.
- **Tests:** wrong current password rejected; correct one succeeds and re-hashes.
- **DoD:** covered by an integration test.
- **Depends on:** 2.6
- **Risk:** Low

#### 2.9 Profile endpoints
- **Goal:** `GET /api/v1/users/me`, `PUT /api/v1/users/me/profile`.
- **Backend:** `UserProfile` CRUD scoped to the authenticated user.
- **Tests:** integration test for read/update, and that a user cannot read/update
  another user's profile.
- **DoD:** authorization boundary covered by a test, not just code review.
- **Depends on:** 2.1, 2.6
- **Risk:** Low

#### 2.10 Role-based authorization
- **Goal:** Server-side role checks on every mutating endpoint; an `ADMIN`-only
  endpoint exists to prove the mechanism.
- **Backend:** method security (`@PreAuthorize` or equivalent) wired to `UserRole`;
  a placeholder `GET /api/v1/admin/ping` requiring `ADMIN`.
- **Tests:** integration test: `USER` role gets 403 on the admin endpoint.
- **DoD:** authorization enforced server-side regardless of what the frontend sends.
- **Depends on:** 2.2, 2.6
- **Risk:** Low

#### 2.11 Rate limiting + lockout on auth endpoints
- **Goal:** Brute-force mitigation on login/forgot-password.
- **Backend:** per-IP/per-account rate limiting (e.g. Bucket4j) and temporary lockout
  after repeated failures.
- **Tests:** integration test asserting the Nth rapid attempt is rejected.
- **DoD:** limits configurable via environment, not hard-coded magic numbers buried in
  a service.
- **Depends on:** 2.6
- **Risk:** Medium

#### 2.12 Angular auth feature
- **Goal:** Register, login, forgot/reset-password, verify-email pages; auth guard;
  HTTP interceptor handling 401 (redirect to login) and the error envelope.
- **Frontend:** `features/auth/*`, `core` interceptor + guard.
- **Tests:** component tests for form validation; guard unit test.
- **DoD:** a user can complete every flow above through the UI.
- **Depends on:** 2.4–2.9, 1.3
- **Risk:** Low

#### 2.13 Angular "Complete your profile" flow
- **Goal:** Post-registration redirect into profile completion (Product Requirements
  §6.1).
- **Frontend:** `features/onboarding/*`.
- **Tests:** component test for the redirect-after-registration behavior.
- **DoD:** new user lands on profile completion right after verifying email.
- **Depends on:** 2.12, 2.9
- **Risk:** Low

#### 2.14 Backend integration test suite for auth
- **Goal:** Testcontainers-backed coverage of 2.4–2.11 as a cohesive suite (not just
  scattered per-task tests).
- **Tests:** consolidated `AuthIntegrationTest` class(es).
- **DoD:** suite green in CI.
- **Depends on:** 2.4–2.11
- **Risk:** Low

#### 2.15 Playwright E2E: registration → verification → login → logout
- **Goal:** The brief §57 critical path, automated.
- **Tests:** Playwright spec against the full local Compose stack (using Mailpit's API
  to fetch the verification link).
- **DoD:** spec green in CI.
- **Depends on:** 2.12, 1.9, 1.10
- **Risk:** Medium (email-in-the-loop E2E tests are the flakiest kind — budget time for
  this)

---

## Phase 3 — Reference / geographic data — ✅ COMPLETE (see [PHASE_3_REPORT.md](PHASE_3_REPORT.md))

#### 3.1 Migration + entity: Country
- **Goal:** `Country` table per DATABASE.md §2.
- **Database/Backend:** migration + JPA entity/repository.
- **Tests:** repository test.
- **DoD:** table exists, unique on `code`.
- **Depends on:** 1.5
- **Risk:** Low

#### 3.2 Seed ISO 3166 countries
- **Goal:** All ISO 3166-1 alpha-2 countries loaded.
- **Database:** a seed migration or a repeatable Flyway script loading a static CSV/JSON
  fixture (not hand-typed SQL for ~250 rows).
- **Tests:** count assertion (expected row count) after seeding.
- **DoD:** `PK`, `IN`, `US`, `UA`, `DE`, `PL` (and the rest) all present.
- **Depends on:** 3.1
- **Risk:** Low

#### 3.3 Migration + entities: CountryGroup, CountryGroupMembership
- **Goal:** Time-bounded group membership per DATABASE.md §2.
- **Database/Backend:** migration + entities/repositories.
- **Tests:** repository test for a membership with `valid_to` set.
- **DoD:** schema supports the Brexit-style membership-ended case.
- **Depends on:** 3.1
- **Risk:** Low

#### 3.4 Seed EU/EEA/Swiss/UK-WA groups
- **Goal:** `EU_MEMBER`, `EEA_EFTA`, `SCHENGEN`, `UK_WITHDRAWAL_AGREEMENT` groups seeded
  with correct current membership (and, where cheap to include, historical boundaries —
  UK's EU membership end date at minimum).
  - **Actual (see ADR-006):** `EU_MEMBER`, `EEA`, `EFTA`, `SCHENGEN` (split, not merged as
    `EEA_EFTA`) plus `EU_EEA_SWISS` (a `CONVENIENCE`-type aggregate). No
    `UK_WITHDRAWAL_AGREEMENT` group — that fact is person-level (an individual's
    residence-rights status), not country-level, so it can't be a `CountryGroup`
    membership at all; it stays a documented open question for whichever future entity
    models individual immigration status.
- **Database:** seed migration.
- **Tests:** "is Germany an EU member today" / "was the UK an EU member in 2026"
  service-level tests.
- **DoD:** `CitizenshipClassification` derivation (ASSESSMENT_DECISION_TREE.md Step 1)
  is backed by real data, not a stub list.
- **Depends on:** 3.3
- **Risk:** Medium (getting historical boundaries slightly wrong is easy to miss in
  review — flag for the Phase 10 legal-review pass too)

#### 3.5 Migration + entities: Region, City, District
- **Goal:** Geographic hierarchy per DATABASE.md §2.
- **Database/Backend:** migration + entities/repositories, `City.is_active` flag.
- **Tests:** repository test.
- **DoD:** schema supports `Poland → Mazowieckie → Warsaw → district` today and
  `Poland → Małopolskie → Kraków` later with no schema change.
- **Depends on:** 3.1
- **Risk:** Low

#### 3.6 Seed Poland → Mazowieckie → Warsaw
- **Goal:** The only active city in V1 is correctly wired.
- **Database:** seed migration: `Region(MAZOWIECKIE)`, `City(WARSAW, is_active=true)`.
- **DoD:** `is_active=true` only on Warsaw.
- **Depends on:** 3.5
- **Risk:** Low

#### 3.7 Seed Warsaw districts
- **Goal:** Official Warsaw districts loaded.
- **Database:** seed migration — **source and verify the official district list**
  (Warsaw's municipal district boundaries) before seeding; don't guess names.
- **DoD:** district list matches the verified official source.
- **Depends on:** 3.6
- **Risk:** Low

#### 3.8 Migration + entity: Jurisdiction
- **Goal:** National/Regional/Municipal scoping entity per DATABASE.md §2, with its
  `CHECK` constraint.
- **Database/Backend:** migration + entity/repository.
- **Tests:** constraint test — inserting a `REGIONAL` row with `city_id` set is
  rejected.
- **DoD:** seed `PL` (national), `PL_MAZOWIECKIE` (regional), `PL_MAZOWIECKIE_WARSAW`
  (municipal).
- **Depends on:** 3.1, 3.5
- **Risk:** Low

#### 3.9 Migration + entities: Authority, Office, OfficeService, ProcedureOffice
- **Goal:** Institutions and physical offices, per DATABASE.md §2.
- **Database/Backend:** migration + entities/repositories.
- **Tests:** repository test.
- **DoD:** schema in place (data seeded next, 3.10).
- **Depends on:** 3.8
- **Risk:** Low
- **Actual:** `Authority`/`Office`/`OfficeService` implemented (plus a promoted
  `ServiceType` reference entity `OfficeService` joins against — see DATABASE.md §2).
  `ProcedureOffice` deferred to Phase 4+, when a `Procedure` identity exists for it to
  reference.

#### 3.10 Seed Mazowieckie Voivodeship Office + known Warsaw offices
- **Goal:** Real office data for the offices the MVP procedures route to.
- **Database:** seed migration sourced from PROCEDURE_CATALOGUE.md's researched
  (currently `DRAFT`) office details; `OfficialSource` rows created alongside, marked
  `DRAFT` until the Phase 10 legal-review pass verifies them.
- **DoD:** at least the Mazowieckie Dept. for Foreigners' Affairs office and one Warsaw
  district Administration & Resident Services delegation exist.
- **Depends on:** 3.9
- **Risk:** Low (content risk, not technical — tracked as a Phase 10 follow-up)
- **Actual:** Only the Mazowieckie Dept. for Foreigners' Affairs office was seeded — the
  brief's own "seed fewer records rather than inventing data" instruction took priority
  over this DoD's second office once no Warsaw district delegation address could be
  independently re-verified in this pass. Tracked as a Phase 10 follow-up alongside the
  district-level PESEL/meldunek/driving-licence routing this task's original DoD implied.

#### 3.11 Reference REST endpoints
- **Goal:** `GET /api/v1/reference/countries` (+ regions/cities/districts/authorities/
  offices as needed by the frontend).
- **Backend:** read-only controllers/DTOs over 3.1–3.10's data.
- **Tests:** integration test per endpoint.
- **DoD:** endpoints documented in OpenAPI (ARCHITECTURE.md §6).
- **Depends on:** 3.2, 3.4, 3.7, 3.10
- **Risk:** Low

#### 3.12 Angular reference-data service + country picker
- **Goal:** Reusable country/region/city picker components consuming 3.11.
- **Frontend:** `core`/`shared` service + a searchable country-select component (used
  later by the assessment wizard, Phase 5).
- **Tests:** component test for search/filter behavior.
- **DoD:** picker used successfully in a throwaway demo page.
- **Depends on:** 3.11
- **Risk:** Low

#### 3.13 Classification derivation tests
- **Goal:** Confirm `CitizenshipClassification` (ASSESSMENT_DECISION_TREE.md Step 1) is
  correctly derived from `CountryGroupMembership` as of a given date.
- **Tests:** backend unit/integration tests: Polish citizen, EU citizen, EEA citizen,
  Swiss citizen, third-country national, and a historical "UK before/after Brexit" case.
- **DoD:** all classification test cases pass.
- **Depends on:** 3.4
- **Risk:** Low

---

## Phase 4 — Procedure / content management — ✅ COMPLETE (see [PHASE_4_REPORT.md](PHASE_4_REPORT.md))

#### 4.1 Migration + entities: ProcedureCategory, Procedure
- **Database/Backend:** per DATABASE.md §3.
- **Tests:** repository test.
- **DoD:** categories from PROCEDURE_CATALOGUE.md seeded (Residence, Work, Study,
  Family, Driving, Administrative, Business, Long-Term Stay).
- **Depends on:** 3.8 (needs `Jurisdiction`)
- **Risk:** Low
- **Actual:** 11 categories seeded (the catalogue's actual category set is finer-grained
  than this task's illustrative 8-item list); 8 MVP procedure identities seeded with
  real jurisdiction tags from PROCEDURE_CATALOGUE.md (5×NATIONAL, 3×MUNICIPAL).

#### 4.2 Migration + entity: ProcedureVersion (with exclusion constraint)
- **Goal:** Versioned procedure content with non-overlapping published ranges.
- **Database:** enable `btree_gist`; add the `EXCLUDE USING gist` constraint from
  DATABASE.md §3.
- **Backend:** entity/repository, status-transition guard in the service layer (no
  direct status column writes outside the publish workflow).
- **Tests:** integration test proving two overlapping `PUBLISHED` versions of the same
  procedure are rejected at the DB level.
- **DoD:** constraint test passes.
- **Depends on:** 4.1
- **Risk:** Medium (exclusion constraints are the least-familiar Postgres feature on
  the team — budget review time)
- **Actual:** implemented with the Active-Version Predicate's established *exclusive*
  `effective_to` convention (`effective_from <= evaluationDate AND (effective_to IS
  NULL OR effective_to > evaluationDate)`) rather than an inclusive comparison, matching
  DATABASE.md §0 and Postgres's default half-open `[)` range semantics — this is a
  restatement of the one existing convention, not a second one. The publish workflow
  also required an explicit `saveAndFlush()` when closing a superseded version's
  `effective_to` immediately before publishing the new one, since Hibernate's automatic
  flush ordering does not guarantee the DB sees that close before the EXCLUDE
  constraint evaluates the new row.

#### 4.3 Migration + entities: ProcedureStep, StepVersion
- **Database/Backend:** per DATABASE.md §3 (identity + per-version snapshot pattern).
- **Tests:** repository test confirming a new `ProcedureVersion` requires its own
  `StepVersion` rows (no silent fallback to a prior version's steps).
- **DoD:** covered by test.
- **Depends on:** 4.2
- **Risk:** Low

#### 4.4 Migration + entities: DocumentRequirement, DocumentRequirementVersion
- **Database/Backend:** per DATABASE.md §3, including `condition_rule_id` FK
  (nullable, wired to `Rule` once Phase 6 exists — leave as a plain nullable FK column
  for now).
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 4.2
- **Risk:** Low
- **Actual:** `condition_rule_id` deliberately omitted rather than added as a
  speculative FK to a not-yet-existing `Rule` table — the brief's own "avoid
  speculative foreign keys" instruction took priority over this task's original
  wording. `DocumentType` was added as a new reusable identity entity (9 seeded codes)
  that `DocumentRequirement` references, so document kinds aren't duplicated free text
  across requirements.

#### 4.5 Migration + entities: Fee, FeeVersion
- **Database/Backend:** per DATABASE.md §3.
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 4.2
- **Risk:** Low
- **Actual:** implemented as its own identity+version pair (`Fee`/`FeeVersion`,
  matching Phase 0's suggested independent design) rather than folding fee fields
  directly onto `ProcedureVersion`, but each `FeeVersion` is scoped to (and published
  alongside) one specific `ProcedureVersion` rather than being independently
  active-resolved on its own timeline the way `Threshold` is — a fee only ever makes
  sense in the context of the procedure version that quotes it, so a second, parallel
  publication lifecycle for fees would have added complexity (a second Active-Version
  Predicate call site, a second set of publish-state edge cases) without a real
  independent-evolution benefit in this dataset.

#### 4.6 Migration + entities: Threshold, ThresholdVersion (with exclusion constraint)
- **Database/Backend:** per DATABASE.md §3, same exclusion-constraint pattern as 4.2.
- **Tests:** integration test: overlapping published threshold values rejected;
  "resolve `BLUE_CARD_MIN_SALARY` as of date X" query test.
- **DoD:** covered by test.
- **Depends on:** 4.1
- **Risk:** Medium (same constraint-family risk as 4.2)
- **Actual:** `Threshold`/`ThresholdVersion` implemented with its own independent
  publish lifecycle and EXCLUDE constraint (unlike `Fee`, see 4.5), including its own
  `PublicationStateMachine`-driven service. No numeric threshold values were seeded —
  no verified figure like `BLUE_CARD_MIN_SALARY` exists in PROCEDURE_CATALOGUE.md yet,
  and the brief instructed against seeding unverified numbers — so this task is
  schema-and-service-complete but has zero rows. Not exposed via a dedicated public or
  internal HTTP endpoint in Phase 4 (no consumer needs it yet; Phase 6/7 will).

#### 4.7 Migration + entities: OfficialSource, SourceVerification
- **Database/Backend:** per DATABASE.md §3, `ON DELETE RESTRICT` from every `*Version`
  table's `source_id` FK.
- **Tests:** integration test: deleting a source referenced by a published version is
  rejected.
- **DoD:** covered by test.
- **Depends on:** 3.8
- **Risk:** Low
- **Actual:** `OfficialSource`/`SourceVerification` support multiple sources per
  content item via PRIMARY/SUPPORTING/OPERATIONAL roles (five join tables, one per
  versioned content type), not a single source FK per version. Also implemented here
  (not called out as a separate numbered task, but required to close the gap 3.9 left
  open): `ProcedureAuthority` (procedure ↔ authority, with a role) and
  `ProcedureVersionOffice` — the latter is the `ProcedureOffice` association deferred
  from task 3.9, renamed and scoped to `ProcedureVersion` rather than bare `Procedure`,
  since which office handles a procedure can itself change between versions. Three new
  roles (`CONTENT_EDITOR`, `LEGAL_REVIEWER`, `ADMIN`) were seeded with no grants to any
  existing user (no self-escalation).

#### 4.8 Publish workflow service + validation
- **Goal:** `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED` transitions with the
  publish-time checks from DATABASE.md / brief §94 (source present, jurisdiction set, no
  broken threshold/source references, sane effective dates).
- **Backend:** a shared `PublishValidationService` usable across
  Procedure/Rule/Threshold/DocumentRequirement/Fee versions (avoid duplicating the same
  five checks five times).
- **Tests:** unit tests for each validation rule; integration test for the full
  transition sequence.
- **DoD:** attempting to publish a version with no source is rejected with a clear
  error, not a generic 500.
- **Depends on:** 4.2, 4.4, 4.5, 4.6, 4.7
- **Risk:** Medium
- **Actual:** transition validation is centralized in `PublicationStateMachine`
  (shared by `ProcedureVersion` and `ThresholdVersion`), and publish-readiness checks
  (source present, effective dates sane, no overlapping published range) live in
  `ProcedurePublishingService`/`ThresholdService` rather than one single cross-entity
  `PublishValidationService` — with only two entities carrying an independent publish
  lifecycle in Phase 4 (`Fee`/`DocumentRequirement` versions are snapshotted with their
  parent `ProcedureVersion`, see 4.5), a shared abstraction would have added an
  interface layer over two call sites without yet proving out a third. Revisit if Rule
  versions (Phase 6) need the same lifecycle. A real bug was found and fixed here:
  passing a detached entity between separate `@Transactional` service methods silently
  dropped a status-transition write (see PHASE_4_REPORT.md).

#### 4.9 Active-Version Predicate repository methods
- **Goal:** One well-tested "get the active version as of date X" query per versioned
  entity (DATABASE.md §0), reused everywhere instead of ad hoc queries.
- **Backend:** generic or per-entity repository methods implementing the predicate from
  DATABASE.md §0.
- **Tests:** unit tests: a `DRAFT` version is never returned; a `PUBLISHED` version with
  a future `effective_from` is not returned until that date; a closed `effective_to` is
  respected.
- **DoD:** this is the method every later phase (5–8) calls — get it right and covered
  before building on it.
- **Depends on:** 4.2, 4.6
- **Risk:** High (this predicate is the single most load-bearing piece of logic in the
  system — an off-by-one here silently leaks draft content or hides published content)

#### 4.10 Public procedure endpoints
- **Goal:** `GET /api/v1/procedures`, `GET /api/v1/procedures/{id}` resolving the active
  version via 4.9.
- **Backend:** controllers/DTOs.
- **Tests:** integration test.
- **DoD:** OpenAPI-documented.
- **Depends on:** 4.9
- **Risk:** Low
- **Actual:** public read endpoints implemented (`ProcedureController`), plus a
  minimal internal content-management API (`ProcedureAdminController` under
  `/api/v1/internal/content/**`, role-gated by CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN) to
  actually drive a version through DRAFT→IN_REVIEW→APPROVED→PUBLISHED→ARCHIVED —
  necessary to have anything to test against 4.9/4.12, but intentionally minimal, not
  the full Phase 9 admin UI/API.

#### 4.11 Angular procedure browser
- **Goal:** "Browse procedures" category tree + procedure detail page (Product
  Requirements §6.4).
- **Frontend:** `features/procedures/*`.
- **Tests:** component test.
- **DoD:** a user can navigate Residence → Temporary Residence → Work without ever
  touching the questionnaire.
- **Depends on:** 4.10
- **Risk:** Low

#### 4.12 Backend integration tests: publish validation + versioning edge cases
- **Goal:** Consolidated coverage of 4.2–4.9 as a suite.
- **Tests:** `ProcedureVersioningIntegrationTest` covering the scenarios named above.
- **DoD:** suite green in CI.
- **Depends on:** 4.8, 4.9
- **Risk:** Low

---

## Phase 5 — Questionnaire engine — ✅ COMPLETE (see [PHASE_5_REPORT.md](PHASE_5_REPORT.md))

The task list below is left as originally planned for traceability; see
[PHASE_5_REPORT.md](PHASE_5_REPORT.md)'s "Deviations" section for where the actual
implementation diverged (most notably: a full `QuestionnaireVersion` lifecycle was added
rather than the version-less design 5.1 originally pointed at DATABASE.md §4 for, no
anonymous-assessment claiming flow was built, and no bulk `PATCH .../answers` endpoint
exists — see ADR-008).

#### 5.1 Migration + entities: Questionnaire, Question, QuestionOption,
QuestionDependency
- **Database/Backend:** per DATABASE.md §4.
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 3.1 (Question fields reference reference data types)
- **Risk:** Low

#### 5.2 Shared condition/dependency evaluator
- **Goal:** One evaluator implementation for the fixed operator vocabulary
  (`EQUALS`...`ANY`), used by both `QuestionDependency` (this phase) and `RuleCondition`
  (Phase 6) — build it once here since the wizard needs it first.
- **Backend:** `rules` (or a new shared `evaluation`) module component with unit tests
  per operator.
- **Tests:** one test per operator, plus nested `ALL`/`ANY`.
- **DoD:** operator coverage complete; Phase 6 reuses this class rather than
  reimplementing it.
- **Depends on:** 5.1
- **Risk:** Medium (getting this reused cleanly by both call sites matters more than
  getting either individually — resist the urge to fork it "just for now")

#### 5.3 Seed MVP questionnaire
- **Goal:** The question set from ASSESSMENT_DECISION_TREE.md Steps 1–5, scoped to the
  8 MVP procedures' inputs.
- **Database:** seed migration with `Question.code`s exactly matching the decision
  tree doc (`CITIZENSHIP_COUNTRY`, `CURRENT_LEGAL_STATUS`, `PRIMARY_PURPOSE`,
  `MONTHLY_GROSS_SALARY`, etc.).
- **DoD:** every `Q_*` code in the decision tree doc that feeds an MVP procedure exists
  as a `Question` row.
- **Depends on:** 5.1
- **Risk:** Low

#### 5.4 Migration + entities: Assessment, AssessmentAnswer
- **Database/Backend:** per DATABASE.md §4, including nullable `user_id` +
  `anonymous_session_token` for pre-registration assessments.
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 5.1
- **Risk:** Low

#### 5.5 Assessment endpoints
- **Goal:** `POST /api/v1/assessments`, `PUT /api/v1/assessments/{id}/answers`.
- **Backend:** controllers/DTOs; anonymous assessments allowed, claimed on
  registration/login (link `anonymous_session_token` → `user_id`).
- **Tests:** integration test for anonymous start + later claiming.
- **DoD:** covered by test.
- **Depends on:** 5.4, 2.6
- **Risk:** Medium (the anonymous-to-claimed transition is the fiddly part)

#### 5.6 Angular generic wizard renderer
- **Goal:** A single Angular component tree that renders any `Question` based on
  `question_type`, hides/shows based on `QuestionDependency` (evaluated client-side via
  the same operator semantics as 5.2 — keep the two implementations behaviorally
  identical even though one is TypeScript and one is Java), and supports the "I don't
  know / not sure" sentinel on every applicable question (brief §77).
- **Frontend:** `features/assessment/*`.
- **Tests:** component tests for show/hide branching and the "not sure" path.
- **DoD:** adding a new `Question` row changes the wizard with zero Angular code
  changes.
- **Depends on:** 5.3, 3.12
- **Risk:** Medium

#### 5.7 Review-answers step + re-entry re-evaluation
- **Goal:** Step 6 of the decision tree — editing an earlier answer re-evaluates
  downstream dependencies without discarding still-valid answers.
- **Frontend:** wizard review step + edit-in-place flow.
- **Tests:** component test: changing `PRIMARY_PURPOSE` away from `WORK` hides
  `MONTHLY_GROSS_SALARY` and clears it; unrelated answers are preserved.
- **DoD:** covered by test.
- **Depends on:** 5.6
- **Risk:** Low

#### 5.8 Backend branching integration tests
- **Goal:** Server-side confirmation that dependent questions are correctly
  gated/ungated (defense in depth — the frontend isn't the only place this must be
  correct, since `PUT /answers` could be called directly).
- **Tests:** integration tests mirroring 5.7's scenario at the API level.
- **DoD:** covered by test.
- **Depends on:** 5.5, 5.2
- **Risk:** Low

#### 5.9 Playwright E2E: complete the wizard
- **Goal:** One full run of the wizard for a scripted "third-country worker" persona,
  ending at Step 7 (handoff to Phase 7's recommendation engine — for now, just confirm
  the assessment reaches `COMPLETED`).
- **Tests:** Playwright spec.
- **DoD:** spec green in CI.
- **Depends on:** 5.6, 5.7
- **Risk:** Low

---

## Phase 6 — Rules engine — ✅ COMPLETE (see [PHASE_6_REPORT.md](PHASE_6_REPORT.md))

The task list below is left as originally planned for traceability; see
[PHASE_6_REPORT.md](PHASE_6_REPORT.md)'s "Deviations" section for where the actual
implementation diverged (most notably: `RuleTargetType` is broader than 6.1 sketched
— `PROCEDURE`/`DOCUMENT_REQUIREMENT`/`STEP`/`FEE`/`THRESHOLD_APPLICABILITY`/`ROUTING`
declared, only `PROCEDURE` evaluated; 6.2's "JSON Schema validation" became a
hand-written structural parser plus a database-backed semantic validator rather than a
JSON Schema document, since the semantic half — unknown fact/threshold/country-group —
needs live repository access a static schema can't express; `NOT` nodes and the
`IS_MEMBER_OF_COUNTRY_GROUP`/`IS_NOT_MEMBER_OF_COUNTRY_GROUP` operators were added beyond
6.4's original scope per the approved Phase 6 brief; 6.8's admin preview endpoint was
implemented as a service method (`RuleEvaluationService.previewEvaluate` /
`RuleEvaluator.previewEvaluate`) rather than an HTTP endpoint — no admin HTTP surface
exists for Rule management at all yet, matching Phase 4's `ThresholdService` precedent of
shipping zero controller until real content needs managing; `RULE_ENGINE_VERSION` was
implemented as `RuleEvaluator.ENGINE_VERSION`).

#### 6.1 Migration + entities: Rule, RuleVersion (JSONB, with exclusion constraint)
- **Database/Backend:** per DATABASE.md §5.
- **Tests:** exclusion-constraint test (same pattern as 4.2/4.6).
- **DoD:** covered by test.
- **Depends on:** 4.9 (reuses the Active-Version Predicate machinery)
- **Risk:** Medium

#### 6.2 JSON Schema validation for condition_tree
- **Goal:** Malformed condition trees are rejected on save, not discovered at
  evaluation time.
- **Backend:** JSON Schema definition + validation in the admin write path (Phase 9
  wires the actual admin endpoint; this task is the schema + validator only, exercised
  via unit tests against fixture JSON).
- **Tests:** valid/invalid fixture-driven unit tests.
- **DoD:** covered by test.
- **Depends on:** 6.1
- **Risk:** Low

#### 6.3 Migration + table: RuleThresholdReference + extraction logic
- **Goal:** Impact-analysis support per DATABASE.md §5.
- **Database/Backend:** migration + a save-time hook walking `condition_tree` and
  populating the reference table.
- **Tests:** unit test: saving a rule referencing `BLUE_CARD_MIN_SALARY` produces the
  expected reference row; a subsequent edit removing that reference removes the row too.
- **DoD:** covered by test.
- **Depends on:** 6.1
- **Risk:** Low

#### 6.4 Rule evaluator
- **Goal:** Walks a `condition_tree`, resolving `reference`s via the Active-Version
  Predicate against `Threshold`, using the shared evaluator from 5.2 for leaf operators
  and adding `ALL`/`ANY` tree recursion.
- **Backend:** `rules` module core class, `evaluate(conditionTree, assessmentAnswers,
  evaluationDate) → MatchResult`.
- **Tests:** unit tests per operator (reuse 5.2's cases), nested `ALL`/`ANY`, threshold
  resolution at a specific date, missing-field-in-answers handling (must produce
  `MORE_INFORMATION_REQUIRED`, not an exception).
- **DoD:** 100% of the operator vocabulary covered by a passing test.
- **Depends on:** 6.1, 5.2, 4.9
- **Risk:** High (this is the core of the product's trust model — under-testing here is
  the single highest-consequence shortcut available in the whole plan)

#### 6.5 Temporal evaluation threading
- **Goal:** `evaluationDate` flows from `Assessment.evaluation_date` through the
  evaluator and every Active-Version Predicate call, so a historical assessment can be
  replayed exactly.
- **Backend:** confirm no code path defaults to "now" except when no explicit date is
  supplied (the live-assessment case).
- **Tests:** integration test: evaluating the same assessment against two different
  `evaluationDate`s (straddling a threshold change fixture) yields different results.
- **DoD:** covered by test.
- **Depends on:** 6.4
- **Risk:** Medium

#### 6.6 Minimal RuleOutcome entity
- **Goal:** Schema placeholder per DATABASE.md §5 — not wired into evaluation logic
  yet.
- **Database/Backend:** migration + entity only.
- **DoD:** table exists; explicitly out of scope for MVP evaluation (documented in a
  code comment pointing at DATABASE.md §5, so nobody "finishes" it accidentally before
  it's needed).
- **Depends on:** 6.1
- **Risk:** Low

#### 6.7 Rule-engine scenario test suite
- **Goal:** The brief §58 scenarios, encoded as real fixtures against real (test) rule
  data.
- **Tests:** scenario tests: third-country student with no job offer does not get an
  EU-citizen registration recommendation; an EU citizen working in Warsaw evaluates
  under the EU free-movement rule set, not the third-country one; a highly-qualified
  applicant below the Blue Card salary threshold fails that specific rule while
  remaining eligible for evaluation under the ordinary work-permit rule.
- **DoD:** all named scenarios pass; test names describe the scenario generically
  (citizenship classification, not "PakistaniStudentTest").
- **Depends on:** 6.4, 6.5
- **Risk:** Medium

#### 6.8 Admin rule-preview endpoint
- **Goal:** `POST /api/v1/admin/rules/{id}/preview` — evaluate a draft rule version
  against a sample answer set without publishing it, so an admin can sanity-check a
  change before review.
- **Backend:** controller wired to 6.4's evaluator against an `APPROVED`-or-earlier
  version explicitly (bypassing the Active-Version Predicate on purpose, admin-only).
- **Tests:** integration test.
- **DoD:** covered by test; endpoint is `ADMIN`-only (2.10).
- **Depends on:** 6.4, 2.10
- **Risk:** Low

---

## Phase 7 — Recommendation engine — ✅ COMPLETE (see [PHASE_7_REPORT.md](PHASE_7_REPORT.md))

The task list below is left as originally planned for traceability; see
[PHASE_7_REPORT.md](PHASE_7_REPORT.md)'s "Deviations" section for where the actual
implementation diverged (most notably: a `RecommendationRun` identity/grouping row was
added that 7.1 didn't originally plan for, since the approved Phase 7 brief requires
immutable historical reproducibility rather than the replace-in-place cache DATABASE.md
§6 originally sketched — see ADR-010; 7.2's single "evaluate endpoint" became four
endpoints — `POST .../recommendation-runs`, `GET .../recommendations/latest`, `GET
.../recommendation-runs` history, `GET /recommendation-runs/{id}` — matching the
approved brief's richer API surface; 7.3's ranking became two classes,
`RecommendationClassifier` + `RecommendationRanker`, kept deliberately separate per the
brief's own "avoid one giant class" instruction).

#### 7.1 Migration + entities: Recommendation, RecommendationReason
- **Database/Backend:** per DATABASE.md §6.
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 6.4, 4.9
- **Risk:** Low

#### 7.2 Evaluate endpoint
- **Goal:** `POST /api/v1/assessments/{id}/evaluate` — runs 6.4's evaluator once per
  candidate procedure (every active `Procedure` whose category matches the assessment's
  `PRIMARY_PURPOSE` answers), persists `Recommendation`/`RecommendationReason`,
  replacing any prior rows for the same assessment (DATABASE.md §6 — not append-only).
- **Backend:** `recommendation` module service + controller.
- **Tests:** integration test: re-running `evaluate` after an answer edit replaces
  rather than duplicates rows.
- **DoD:** covered by test.
- **Depends on:** 7.1, 6.4, 5.5
- **Risk:** Medium

#### 7.3 Match-type ranking logic
- **Goal:** Categorize each candidate as `PRIMARY_MATCH` / `POSSIBLE_ALTERNATIVE` /
  `MORE_INFORMATION_REQUIRED` / `NOT_APPLICABLE` per Product Requirements §6.3/§7 — no
  numeric confidence anywhere.
- **Backend:** ranking rule (e.g. a fully-matched rule with no missing fields is
  `PRIMARY_MATCH`; a fully-matched rule for a secondary purpose is
  `POSSIBLE_ALTERNATIVE`; any unresolved "not sure"/missing answer feeding the
  eligibility rule is `MORE_INFORMATION_REQUIRED`).
- **Tests:** unit tests per category.
- **DoD:** covered by test; code review confirms no percentage/score field was
  reintroduced.
- **Depends on:** 7.2
- **Risk:** Medium

#### 7.4 Recommendations read endpoint
- **Goal:** `GET /api/v1/assessments/{id}/recommendations`.
- **Backend:** controller/DTO assembling `Recommendation` + `RecommendationReason` +
  linked `OfficialSource` details.
- **Tests:** integration test.
- **DoD:** OpenAPI-documented.
- **Depends on:** 7.2
- **Risk:** Low

#### 7.5 Angular recommendations page
- **Goal:** Ranked results with "why" (matched/failed/missing) and source links, using
  the qualified language from Product Requirements §5 ("this appears to be...", never a
  certainty).
- **Frontend:** `features/recommendations/*`.
- **Tests:** component test asserting no numeric confidence is ever rendered.
- **DoD:** covered by test.
- **Depends on:** 7.4
- **Risk:** Low

#### 7.6 Playwright E2E: wizard → recommendation
- **Goal:** End-to-end for one scripted scenario from 6.7.
- **Tests:** Playwright spec.
- **DoD:** spec green in CI.
- **Depends on:** 7.5, 5.9
- **Risk:** Low

---

## Phase 8 — User cases + checklist — ✅ COMPLETE (see [PHASE_8_REPORT.md](PHASE_8_REPORT.md))

The task list below is left as originally planned for traceability; see
[PHASE_8_REPORT.md](PHASE_8_REPORT.md)'s "Deviations" section for where the actual
implementation diverged (most notably: `UserCaseRequirementSnapshot`'s single JSONB-
array-of-version-ids row became a real `UserCaseSnapshotRevision` identity row plus
per-item `UserCaseStep`/`UserCaseDocument`/`UserCaseFee` rows, giving revisions a natural
home for the explicit-upgrade flow a single row couldn't represent — see ADR-011;
`UserCaseFee` was added beyond the original 8.1-8.3 scope since Phase 4 does model fees;
no district/office-selection case-setup screen was built (brief §18/§97, deferred);
requirement-change detection covers step/document/fee content only, not a `Procedure`'s
own title/summary or source-record changes).

#### 8.1 Migration + entities: UserCase, UserCaseRequirementSnapshot
- **Database/Backend:** per DATABASE.md §8.
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 4.9, 7.1
- **Risk:** Low

#### 8.2 Migration + entities: UserCaseStep, UserCaseDocument, UserCaseEvent
- **Database/Backend:** per DATABASE.md §8.
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 8.1
- **Risk:** Low

#### 8.3 Case creation endpoint
- **Goal:** `POST /api/v1/cases` — from a `Recommendation` or a directly browsed
  `Procedure`; builds the `UserCaseRequirementSnapshot` by resolving every relevant
  identity's Active-Version at creation time and copying the version IDs.
- **Backend:** `case` module service.
- **Tests:** integration test asserting the snapshot's version IDs match what the
  Active-Version Predicate returns at that moment.
- **DoD:** covered by test.
- **Depends on:** 8.1, 4.9
- **Risk:** Medium

#### 8.4 Case read endpoints
- **Goal:** `GET /api/v1/cases`, `GET /api/v1/cases/{id}`.
- **Backend:** controllers/DTOs, owner-only authorization.
- **Tests:** integration test including the authorization boundary.
- **DoD:** covered by test.
- **Depends on:** 8.3
- **Risk:** Low

#### 8.5 Step/document status update endpoints
- **Goal:** `PATCH /api/v1/cases/{id}/steps/{stepId}`, `PATCH
  /api/v1/cases/{id}/documents/{documentId}`, each writing a `UserCaseEvent`.
- **Backend:** service + controllers.
- **Tests:** integration test, including the event-log side effect.
- **DoD:** covered by test.
- **Depends on:** 8.2, 8.3
- **Risk:** Low

#### 8.6 Requirements-changed diff endpoint
- **Goal:** `GET /api/v1/cases/{id}/requirements-changes` — compares
  `UserCaseRequirementSnapshot`'s stored version IDs against the current Active-Version
  result for the same identities; returns an added/changed/removed diff.
- **Backend:** service in `case` module.
- **Tests:** integration test: publish a new `DocumentRequirementVersion` for a
  procedure after a case exists, confirm the diff reports it; confirm an untouched case
  reports no diff.
- **DoD:** covered by test — this is the concrete proof of brief §36's "requirements
  have changed" behavior.
- **Depends on:** 8.3, 4.8
- **Risk:** Medium

#### 8.7 "Apply updated requirements" action
- **Goal:** User-initiated, opt-in update of a case's snapshot to the current version,
  logged as a `UserCaseEvent`; never automatic.
- **Backend:** endpoint + service.
- **Tests:** integration test: old checklist items not in the new version are removed
  (or archived, per final UX decision) with a recorded event.
- **DoD:** covered by test.
- **Depends on:** 8.6
- **Risk:** Low

#### 8.8 Angular case dashboard + detail
- **Goal:** "My cases" list, case detail with steps + checklist, and the
  requirements-changed banner/diff view (Product Requirements §6.5).
- **Frontend:** `features/cases/*`.
- **Tests:** component tests.
- **DoD:** covered by test.
- **Depends on:** 8.4, 8.5, 8.6
- **Risk:** Low

#### 8.9 Backend integration test suite for cases
- **Goal:** Consolidated coverage of 8.3–8.7.
- **Tests:** `UserCaseIntegrationTest` suite.
- **DoD:** green in CI.
- **Depends on:** 8.3–8.7
- **Risk:** Low

#### 8.10 Playwright E2E: create case → mark documents ready
- **Goal:** Recommendation → case creation → checklist progress, end to end.
- **Tests:** Playwright spec.
- **DoD:** green in CI.
- **Depends on:** 8.8, 7.6
- **Risk:** Low

---

## Phase 9 — Admin panel — ✅ COMPLETE (see [PHASE_9_REPORT.md](PHASE_9_REPORT.md))

Delivered: `AuditLog` + `AdminReview` (append-only, entity-agnostic across the four content
types) and `ContentReviewCoordinator` (self-approval prevention, centralized once); a full
`/api/v1/admin/**` surface (list/detail/diff/impact/validate/dry-run for
Procedure/Rule/Threshold/Questionnaire/Source, plus review queue, audit log, and role
management), reusing rather than duplicating the pre-existing Phase 4-8 lifecycle
(`PublicationStateMachine`, publish-readiness validation); and a real Angular `/admin` panel
(dashboard, procedures editor with steps/documents/fees/sources tabs, a structured Rule
condition builder with dry-run, threshold/source/questionnaire management, reviews, audit,
users). Deviations (structured Rule builder covers one ALL/ANY group with a JSON fallback for
anything more complex; Threshold has no version-copy action or VERIFIED-source publish gate;
Questionnaire question/dependency editing stays read-only; content created through the
pre-Phase-9 `/api/v1/internal/content/**` endpoints is not retrofitted into the audit trail) are
documented in full in PHASE_9_REPORT.md - see there before starting Phase 10.

The task breakdown below is retained as the original plan; the report above reflects what was
actually built and where it differs.

#### 9.1 Migration + entities: AdminReview, AuditLog
- **Database/Backend:** per DATABASE.md §9.
- **Tests:** repository test.
- **DoD:** covered by test.
- **Depends on:** 2.10
- **Risk:** Low

#### 9.2 Audit-logging aspect
- **Goal:** Every admin mutating endpoint automatically writes an `AuditLog` row
  (before/after state) without each controller doing it by hand.
- **Backend:** an AOP aspect or a shared base service intercepting admin writes.
- **Tests:** unit test confirming the aspect fires; integration test on one real admin
  endpoint.
- **DoD:** covered by test.
- **Depends on:** 9.1
- **Risk:** Medium

#### 9.3 Admin CRUD: procedures, steps, documents, fees, thresholds, rules, sources
- **Goal:** Draft-creation and edit endpoints for every content type in Phase 4/6.
- **Backend:** `admin` module controllers delegating to the existing `procedure`/
  `rules`/`source` services (no duplicated business logic).
- **Tests:** integration test per entity type (can share a parameterized test base).
- **DoD:** covered by test; every write goes through 9.2's audit aspect.
- **Depends on:** 4.1–4.7, 6.1, 9.2
- **Risk:** Medium

#### 9.4 Admin review endpoints
- **Goal:** Record an `AdminReview` decision (`APPROVED`/`REJECTED`/
  `CHANGES_REQUESTED`) against a draft version.
- **Backend:** controller/service.
- **Tests:** integration test.
- **DoD:** covered by test.
- **Depends on:** 9.1, 9.3
- **Risk:** Low

#### 9.5 Publish endpoint + version-diff preview
- **Goal:** `POST /api/v1/admin/procedure-versions/{id}/publish` (and equivalents for
  rule/threshold/document/fee versions) enforcing 4.8's validation; a preview endpoint
  showing the diff against the currently active version before publishing.
- **Backend:** controller wired to `PublishValidationService` (4.8).
- **Tests:** integration test: publish blocked with a clear validation error when a
  source is missing or a referenced threshold doesn't exist.
- **DoD:** covered by test.
- **Depends on:** 4.8, 9.3
- **Risk:** Medium

#### 9.6 Angular admin module
- **Goal:** List/edit screens for procedures, rules, sources; a version-compare view;
  a publish action surfacing validation errors from 9.5.
- **Frontend:** `features/admin/*`, more desktop-oriented per ARCHITECTURE.md §10.
- **Tests:** component tests for the publish-blocked-on-validation-error path.
- **DoD:** covered by test.
- **Depends on:** 9.3, 9.5
- **Risk:** Medium

#### 9.7 Admin route/role guard
- **Goal:** `/admin/**` unreachable without `ADMIN` role, both frontend guard and
  backend authorization (2.10) — frontend guard is UX only, never the actual boundary.
- **Frontend:** route guard.
- **Tests:** guard unit test.
- **DoD:** covered by test.
- **Depends on:** 9.6, 2.10
- **Risk:** Low

#### 9.8 Backend integration tests: admin authorization + publish validation
- **Goal:** Consolidated coverage of 9.3–9.5's authorization and validation paths.
- **Tests:** suite confirming non-admin gets 403 everywhere under `/admin/**`, and each
  publish-validation rule from 4.8 has a corresponding negative test here too.
- **DoD:** green in CI.
- **Depends on:** 9.3–9.5
- **Risk:** Low

#### 9.9 Playwright E2E: admin publishes a change, users see it correctly
- **Goal:** Admin drafts a document-requirement change → publishes → a *new* case
  reflects it, while an *existing* case flags "requirements changed" instead of
  silently updating (this is the single most important behavior in the whole product to
  prove end-to-end).
- **Tests:** Playwright spec spanning admin + user flows in one scenario.
- **DoD:** green in CI.
- **Depends on:** 9.6, 8.6
- **Risk:** Medium

---

## Phase 10 — Warsaw procedure content — ⚠️ SUBSTANTIALLY COMPLETE, ONE GAP DISCLOSED (see [PHASE_10_REPORT.md](PHASE_10_REPORT.md))

Real, sourced content published for 4 of the 5 first-release procedures (PESEL,
Meldunek, EU citizen residence registration, Temporary residence and work — plus one
real `Threshold`); the 5th (Temporary residence for studies) reached
`READY_FOR_PUBLICATION`, correctly held by the new VERIFIED-primary-source publish gate.
**No `Rule` was authored for any of them** — see PHASE_10_REPORT.md's "Rules &
Recommendation/Case Validation" before starting further procedure work: none of this
phase's content is reachable through the recommendation engine or `UserCase` creation
yet. EU Blue Card, Family Reunification, and Foreign Driving Licence Exchange (the
other 3 of the original 8 MVP procedures) remain untouched, out of scope for this pass.

Each of the remaining MVP procedures still gets its own task: verify sources directly, write the
dossier (ARCHITECTURE.md §12 format) under `docs/procedures/`, then encode it as real
`Procedure`/`ProcedureVersion`/`Rule`/`DocumentRequirement`/`Fee`/`Threshold` data
through the Phase 9 admin tooling (dogfooding it), publishing only after the dossier's
sources are `VERIFIED`, not `DRAFT`.

#### 10.1 Legal-review pass: verify MVP sources
- **Goal:** Move every `OfficialSource` behind the 8 MVP procedures from `DRAFT` to
  `VERIFIED` (or `NEEDS_REVIEW` with notes) by reading the primary `.gov.pl`/
  `udsc.gov.pl`/`mos.cudzoziemcy.gov.pl`/`warszawa19115.pl` page directly, per
  ARCHITECTURE.md §8's Source Freshness process.
- **DoD:** PROCEDURE_CATALOGUE.md's status column updated accordingly; nothing in Phase
  10.2–10.9 is published on a still-`DRAFT` source.
- **Depends on:** 4.7
- **Risk:** High (this is the one task in the whole plan that is pure legal-accuracy
  risk, not engineering risk — do not compress the time budgeted for it)

#### 10.2 Encode: Temporary Residence and Work
- **Goal:** Full dossier + published `ProcedureVersion` with real steps, documents,
  fee, and the `TR_WORK_BASE_ELIGIBILITY`-style eligibility rule (referencing
  `TR_WORK_MIN_SALARY` threshold).
- **Tests:** scenario test(s) specific to this procedure added to 6.7's suite.
- **DoD:** end-to-end wizard → recommendation → case works for this procedure.
- **Depends on:** 10.1, 9.5
- **Risk:** Medium

#### 10.3 Encode: EU Blue Card
- Same shape as 10.2, including the `BLUE_CARD_MIN_SALARY` threshold and its
  "highly qualified" condition. **Risk:** Medium. **Depends on:** 10.1, 9.5

#### 10.4 Encode: Temporary Residence for Studies
- Same shape as 10.2. **Risk:** Medium. **Depends on:** 10.1, 9.5

#### 10.5 Encode: Family Reunification (spouse of Polish citizen)
- Same shape as 10.2 — explicitly verify the rule set does **not** get shared with the
  EU-family-member track (ASSESSMENT_DECISION_TREE.md Step 4c). **Risk:** Medium.
  **Depends on:** 10.1, 9.5

#### 10.6 Encode: EU Citizen Residence Registration
- Same shape as 10.2, on the EU free-movement rule set, not the third-country one.
  **Risk:** Medium. **Depends on:** 10.1, 9.5

#### 10.7 Encode: PESEL
- Municipal-jurisdiction procedure; office routing via `ProcedureOffice` (Śródmieście
  district case). **Risk:** Low. **Depends on:** 10.1, 9.5, 3.10

#### 10.8 Encode: Meldunek (temporary registration)
- Municipal-jurisdiction procedure; citizenship-group-dependent deadline (4 vs 30 days)
  modeled as a rule condition on the document/step requirement, not a hard-coded
  branch. **Risk:** Medium. **Depends on:** 10.1, 9.5, 3.10

#### 10.9 Encode: Driving Licence Exchange (convention + non-convention)
- Two `DocumentRequirementVersion`/step branches under one `Procedure`, gated by a
  `DrivingLicenceRecognitionRule`-style condition on issuing country. **Risk:** Medium.
  **Depends on:** 10.1, 9.5, 3.10

#### 10.10 Cross-procedure QA pass
- **Goal:** Every MVP procedure has a `VERIFIED` source trail, passes publish
  validation, and completes the full wizard → recommendation → case journey.
- **Tests:** the full 6.7/9.9-style scenario suite, one pass per MVP procedure.
- **DoD:** all 8 green.
- **Depends on:** 10.2–10.9
- **Risk:** Medium

---

## Phase 11 — Testing

#### 11.1 Backend unit coverage pass
- **Goal:** Services, the rule evaluator, mappers, and validators reach an agreed
  coverage bar (a number to set once the codebase exists — don't invent a target here).
- **Tests:** gap-filling unit tests identified via a coverage report.
- **DoD:** CI coverage gate passes.
- **Depends on:** Phases 2–9
- **Risk:** Low

#### 11.2 Testcontainers integration suite completeness pass
- **Goal:** Every repository and every Flyway migration is exercised against real
  Postgres, not just the ones already covered incidentally by earlier phases.
- **Tests:** gap-filling integration tests, plus a "migrate from scratch" test and a
  "migrate on top of production-shaped seed data" test.
- **DoD:** suite green.
- **Depends on:** Phases 2–9
- **Risk:** Low

#### 11.3 Rule-engine scenario suite expansion
- **Goal:** Extend 6.7/10.10's scenarios to cover every MVP procedure's edge cases
  (missing answers, "not sure" sentinels, boundary threshold values).
- **Tests:** scenario tests.
- **DoD:** suite green.
- **Depends on:** 10.10
- **Risk:** Medium

#### 11.4 Frontend test completeness pass
- **Goal:** Wizard, recommendations, and case-checklist components have real test
  coverage, not just the smoke tests added per-phase.
- **Tests:** component/service/guard tests.
- **DoD:** CI coverage gate passes for the frontend too.
- **Depends on:** Phases 5, 7, 8
- **Risk:** Low

#### 11.5 Full Playwright E2E suite
- **Goal:** The brief §57 critical-path list, complete: registration, login, complete
  profile, wizard, recommendation, case creation, checklist update, logout/login
  persistence, admin draft→publish→user-sees-update.
- **Tests:** consolidate 2.15/5.9/7.6/8.10/9.9 into one maintained suite plus any gaps.
- **DoD:** suite green in CI, running on every PR (not just nightly).
- **Depends on:** 2.15, 5.9, 7.6, 8.10, 9.9
- **Risk:** Medium

#### 11.6 CI gate consolidation
- **Goal:** The full test pyramid (11.1–11.5) blocks merge to `main`.
- **Backend/Frontend:** CI workflow updated to run and gate on all suites.
- **DoD:** a deliberately failing test on a throwaway branch blocks its PR.
- **Depends on:** 11.1–11.5
- **Risk:** Low

---

## Phase 12 — Security / GDPR hardening

#### 12.1 Security headers + production CORS lockdown
- **Backend:** CSP, `X-Content-Type-Options`, `Referrer-Policy`, HSTS; CORS restricted
  to the real frontend origin(s) in production.
- **Tests:** integration test asserting headers are present.
- **DoD:** covered by test. **Depends on:** 2.6. **Risk:** Low

#### 12.2 CSRF review for the cookie session model
- **Goal:** Confirm the CSRF posture chosen in 2.6 is actually correct end-to-end
  (double-submit token, `SameSite`, or framework default — whichever was chosen), not
  assumed.
- **Tests:** integration test attempting a cross-origin mutating request, expecting
  rejection.
- **DoD:** covered by test. **Depends on:** 2.6. **Risk:** Medium

#### 12.3 Rate limiting/lockout tuning
- **Goal:** Revisit 2.11's limits with real numbers, add basic abuse monitoring/alerts.
- **DoD:** limits documented in `docs/security/SECURITY.md` (12.8). **Depends on:**
  2.11. **Risk:** Low

#### 12.4 Dependency vulnerability scanning in CI
- **Backend/Frontend:** `mvn dependency-check` / `npm audit` (or equivalent) wired into
  CI, failing on high-severity findings.
- **DoD:** CI job green with current dependencies; documented exception process for
  false positives. **Depends on:** 1.8. **Risk:** Low

#### 12.5 Account data export (GDPR)
- **Backend:** `GET /api/v1/users/me/export` — profile, assessments, cases, consents,
  in a portable format; explicitly excludes other users' data and internal admin
  fields.
- **Tests:** integration test. **DoD:** covered by test. **Depends on:** 2.9, 8.4.
  **Risk:** Low

#### 12.6 Account deletion flow
- **Backend:** soft-delete per DATABASE.md §0 (`status = DELETED`, PII scrub), keeping
  `UserCase`/`AuditLog` referential integrity intact as a tombstone.
- **Tests:** integration test confirming scrubbed fields and intact FKs.
- **DoD:** covered by test. **Depends on:** 2.3. **Risk:** Medium

#### 12.7 Log-scrub audit
- **Goal:** Confirm no password, token, or passport-number value has ever been logged,
  across every module.
- **Backend:** repo-wide review + a lint rule or test asserting sensitive DTO fields
  are marked `@ToString.Exclude`/excluded from log serialization.
- **DoD:** review complete, gap fixes merged. **Depends on:** Phases 2–9. **Risk:** Low

#### 12.8 Write docs/security/SECURITY.md
- **Goal:** Document the as-built security posture (12.1–12.7 plus 2.6/2.11).
- **DoD:** file exists and matches the actual implementation, not aspirations.
  **Depends on:** 12.1–12.7. **Risk:** Low

#### 12.9 Pre-launch security review
- **Goal:** A dedicated review pass (the project's `security-review` skill, or an
  external review) before any production traffic.
- **DoD:** findings triaged and, for anything high-severity, fixed before Phase 13's
  production deploy. **Depends on:** 12.8. **Risk:** Medium

---

## Phase 13 — Deployment

#### 13.1 Production Dockerfiles
- **Backend/Frontend:** multi-stage builds producing minimal production images.
- **DoD:** images build in CI. **Depends on:** 1.1, 1.2. **Risk:** Low

#### 13.2 Reverse proxy + HTTPS
- **Infra:** Caddy or Nginx config, automated certificate issuance.
- **DoD:** HTTPS works against a real domain in staging. **Depends on:** 13.1, 13.6.
  **Risk:** Medium

#### 13.3 Production hosting decision + provisioning scripts
- **Goal:** Pick a concrete target (single-VPS Docker Compose or managed
  services) — architecture stays cloud-neutral either way (ARCHITECTURE.md §13).
- **DoD:** provisioning scripted/documented in `infra/production/`. **Depends on:**
  13.1. **Risk:** Medium

#### 13.4 Database backup + restore verification
- **Goal:** Automated backups with a **tested** restore procedure, not just a cron job
  that's assumed to work.
- **DoD:** a documented restore drill actually performed once. **Depends on:** 13.3.
  **Risk:** High (an untested backup is not a backup)

#### 13.5 Secrets management
- **Goal:** No secret ever committed; production secrets injected via environment/
  secret manager.
- **DoD:** `.env.example` audit confirms no real values ever landed in git history.
  **Depends on:** 1.7. **Risk:** Medium

#### 13.6 Staging environment
- **Goal:** A full staging deploy, smoke-tested with the Playwright suite (11.5)
  against it.
- **DoD:** staging green. **Depends on:** 13.1, 13.3, 13.5. **Risk:** Medium

#### 13.7 Production environment
- **Goal:** Production stood up behind a real domain, smoke-tested.
- **DoD:** production green, 12.9's review findings addressed first. **Depends on:**
  13.2, 13.6, 12.9. **Risk:** High

#### 13.8 CD pipeline
- **Goal:** Merge-to-`main` builds and auto-deploys to staging; production deploy is an
  explicit, gated action (never automatic).
- **Backend/Frontend:** `.github/workflows/cd.yml`.
- **DoD:** a staging deploy happens automatically from a real merge; a production
  deploy requires explicit approval. **Depends on:** 13.6, 11.6. **Risk:** Medium

---

## Phase 14 — Monitoring / analytics

#### 14.1 Correlation ID / structured logging review
- **Goal:** Confirm 1.6's baseline actually threads a correlation ID through every
  request in production, not just the health endpoint.
- **DoD:** a real request's log lines are traceable end-to-end. **Depends on:** 13.7.
  **Risk:** Low

#### 14.2 Error tracking integration
- **Backend/Frontend:** wire an error-tracking service (or self-hosted equivalent);
  confirm 12.7's log-scrub rules also apply to error reports.
- **DoD:** a deliberately triggered error appears in the tracker without leaking PII.
  **Depends on:** 12.7. **Risk:** Low

#### 14.3 Actuator metrics, internal-only
- **Goal:** Metrics available to operators, not the public internet.
- **DoD:** confirmed via an external port scan/firewall check. **Depends on:** 1.6,
  13.2. **Risk:** Medium

#### 14.4 Analytics event emission
- **Goal:** The named events from Product Requirements/brief §55
  (`ASSESSMENT_STARTED`, `ASSESSMENT_COMPLETED`, `RECOMMENDATION_VIEWED`,
  `CASE_CREATED`, `DOCUMENT_MARKED_READY`, `PROCEDURE_VIEWED`, `SOURCE_CLICKED`), with
  no answer content in the payload.
- **Tests:** unit test asserting a sensitive field never appears in an emitted event.
- **DoD:** covered by test. **Depends on:** Phases 5, 7, 8. **Risk:** Low

#### 14.5 Source-freshness dashboard
- **Goal:** Admin-facing `Fresh / Review soon / Overdue review / Source unavailable`
  view over `OfficialSource`/`SourceVerification` (ARCHITECTURE.md §8).
- **Frontend/Backend:** admin screen + supporting endpoint.
- **DoD:** correctly reflects seeded fixture data with known ages. **Depends on:** 4.7,
  9.6. **Risk:** Low

#### 14.6 Uptime/health alerting
- **Goal:** An operator is notified if `/actuator/health` goes red in production.
- **DoD:** a deliberately induced outage triggers an alert in staging. **Depends on:**
  13.7. **Risk:** Low

---

## Phase 15 — Monetisation (scaffolding only)

Per Product Requirements' non-scope: no payment feature ships in MVP. These tasks are
deliberately design-only, to avoid the "overengineering the MVP" trap the brief warns
against.

#### 15.1 Plan/Subscription/Entitlement/Payment schema design spike
- **Goal:** A design doc (not a migration) sketching these entities against real MVP
  usage patterns once they exist to design against.
- **DoD:** design doc reviewed; no code merged. **Depends on:** Phase 10 in production
  long enough to have real usage data. **Risk:** Low

#### 15.2 Payment provider selection spike
- **Goal:** A short comparison note, not an integration.
- **DoD:** decision recorded as a future ADR candidate. **Depends on:** 15.1. **Risk:**
  Low

#### 15.3 Feature-flag scaffolding
- **Goal:** A lightweight flag mechanism so a future premium gate doesn't require a
  rearchitecture, without building the gate itself now.
- **Backend:** a simple `FeatureFlag` lookup service (config- or DB-backed).
- **DoD:** one inert flag exists and is toggled in a test, proving the mechanism works,
  with nothing in the product actually gated by it yet. **Depends on:** —. **Risk:** Low
