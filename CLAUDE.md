# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Foreigner Warsaw** — a guided-eligibility and case-tracking web app for foreigners in
Warsaw, Poland (V1 scope: Warsaw only, architected for later multi-city/multi-country
expansion). Read these before making changes, in this order:

1. [docs/product/PRODUCT_REQUIREMENTS.md](docs/product/PRODUCT_REQUIREMENTS.md) — problem, scope, MVP, non-scope
2. [docs/product/PROCEDURE_CATALOGUE.md](docs/product/PROCEDURE_CATALOGUE.md) — which immigration/admin procedures are in scope, their jurisdiction level, and research/sourcing status
3. [docs/product/ASSESSMENT_DECISION_TREE.md](docs/product/ASSESSMENT_DECISION_TREE.md) — the guided-questionnaire design
4. [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) — modular monolith layout, rules engine, API, versioning, jurisdiction model
5. [docs/database/DATABASE.md](docs/database/DATABASE.md) — the authoritative schema design: every entity, the Active-Version Predicate, provenance chains, ER diagrams
6. [docs/product/IMPLEMENTATION_PLAN.md](docs/product/IMPLEMENTATION_PLAN.md) — the full phase-by-phase task breakdown; find the next task here before starting new work
7. [docs/architecture/ADR/](docs/architecture/ADR/) — the "why" behind the foundational decisions

**Current status: Phase 10.5 (production Rule wiring) complete** — see
[docs/product/PHASE_10_5_REPORT.md](docs/product/PHASE_10_5_REPORT.md). Auth (Phase 2),
reference data (Phase 3), versioned procedure content (Phase 4), the questionnaire/
assessment engine (Phase 5), the deterministic rules engine (Phase 6), the recommendation
engine (Phase 7), user cases with a personalized checklist (Phase 8), the admin
content-governance panel (Phase 9, see
[PHASE_9_REPORT.md](docs/product/PHASE_9_REPORT.md)), and real Warsaw MVP legal content
(Phase 10, see [PHASE_10_REPORT.md](docs/product/PHASE_10_REPORT.md)) all work end to
end. Phase 10 authored real, sourced legal content for four of five first-release
procedures (PESEL, Meldunek, EU citizen residence registration, Temporary residence and
work, plus a real minimum-wage `Threshold`) but disclosed a significant gap: no
eligibility `Rule` existed, so none was reachable through the recommendation engine or
`UserCase` creation. **Phase 10.5 closed that gap** — real production `Rule`s (8 total)
now target four of the five procedures (all `PUBLISHED`), one new `QuestionnaireVersion`
was added (a single `GET_MELDUNEK` goal option) through the real Admin workflow, and a
real, browser-driven Playwright test proves the full production pipeline end to end:
assessment → real Rule evaluation → real recommendation → real `UserCase` with a real
checklist. Temporary residence for studies remains `READY_FOR_PUBLICATION` (its two
Rules are `APPROVED` but not published, mirroring the Procedure's own held-back state) —
see PHASE_10_5_REPORT.md's Phase 11 Readiness verdict before starting Phase 11 or
publishing further procedures.

## Commands

Full walkthrough: [docs/development/LOCAL_SETUP.md](docs/development/LOCAL_SETUP.md)
(includes a real environment gotcha — pre-existing `DB_USERNAME`/`DB_PASSWORD` OS env
vars can shadow the local-profile defaults).

```bash
cp .env.example .env && docker compose up -d      # Postgres 18 + Mailpit

cd backend && export SPRING_PROFILES_ACTIVE=local && ./mvnw spring-boot:run
cd backend && ./mvnw verify                        # unit + Testcontainers PG integration + Spotless check
cd backend && ./mvnw spotless:apply                # auto-fix formatting

cd frontend && npm install && npm start
cd frontend && npm run lint
cd frontend && npm test -- --no-watch
cd frontend && npm run build
cd frontend && npx playwright install chromium && npm run e2e   # backend + Postgres + Mailpit must be running
```

No Maven install required — `backend/mvnw`/`mvnw.cmd` bootstrap the pinned Maven
version themselves. `backend/target` occasionally hits a transient Windows file-lock on
`mvnw clean` right after a build; `rm -rf backend/target` and retry, it's not a code
issue.

`ng serve`/`npm start` proxies `/api` and `/actuator` to `localhost:8080` (see
`frontend/proxy.conf.json`) — the browser only ever talks to `localhost:4200`. Don't
point the frontend straight at `localhost:8080`; Angular's XSRF interceptor refuses to
attach its header on a genuinely cross-origin request, so every unsafe request would
fail CSRF validation (see ADR-005).

## Non-obvious rules for working in this repo

- **Never hard-code legal/administrative content in application code.** No permit rule,
  document requirement, fee, threshold, or nationality-specific exception belongs in a
  Java `if` statement or an Angular template — it belongs in the database as a versioned,
  sourced `Rule`/`DocumentRequirement`/`Fee`/`Threshold` row (see ARCHITECTURE.md §7–§8).
  If you're about to write `if (nationality == "PK")` or `if (salary > 13000)`, stop —
  that's a `CountrySpecificRule` row and a `Threshold` reference, respectively.
- **Never fabricate a legal/procedural fact.** Every requirement, fee, deadline, or
  document listed anywhere user-facing must trace to an `OfficialSource`. When adding or
  changing procedure content, research current official sources first (priority order:
  Polish legislation → Office for Foreigners/UDSC → MOS (mos.cudzoziemcy.gov.pl) →
  gov.pl → Mazowieckie Voivodeship Office → Warsaw municipal gov / Warszawa 19115).
  Blogs, law firms, and forums may help identify what to ask about, but are never the
  cited source. A procedure isn't implementation-ready until it has a dossier under
  `docs/procedures/<category>/<procedure>.md` (structure in ARCHITECTURE.md §12) with
  sources verified, not just found.
- **Never let an LLM/AI decide eligibility.** The rules engine (deterministic,
  database-driven conditions) is the only eligibility decision-maker (ADR-003). AI, if
  used at all, explains or summarizes over already-approved content and never
  auto-publishes a legal-content change — a human approves every publish (ADR-004).
- **The Active-Version Predicate is the single most load-bearing piece of logic in the
  system** (docs/database/DATABASE.md §0): production code only ever reads a `*Version`
  row where `status = 'PUBLISHED' AND effective_from <= evaluationDate AND
  (effective_to IS NULL OR effective_to > evaluationDate)`. Every new query against a
  versioned table (Procedure/Rule/DocumentRequirement/Fee/Threshold) must go through
  this predicate, not a bespoke "get the latest" query — a shortcut here either leaks
  draft content or hides published content.
- **Never silently mutate an existing user's case** when the underlying procedure is
  republished. Cases snapshot the procedure/rule version active at creation and surface
  changes as an explicit, opt-in diff (ARCHITECTURE.md §5).
- **EU/EEA/Swiss free-movement rules and third-country-national rules are separate rule
  sets**, even where the questions look similar — do not merge them for convenience
  (ASSESSMENT_DECISION_TREE.md, Step 2).
- **Work in phases, not all at once.** Per the project's own operating model: before
  implementing a phase, inspect what already exists, state the objective, implement,
  build, run tests, fix failures, then update docs and propose the next phase. Don't
  generate large swaths of the application speculatively ahead of the current phase.
- **If you find existing code in a later session**, inspect it (build files, migrations,
  security config, entities, routes, env files, tests) and report existing/missing/
  incorrect/risky/recommended *before* modifying it — don't overwrite blindly.
- **401 vs 403 vs 404, by design** (see SecurityConfig's Javadoc): an unauthenticated
  request to any non-public path gets 401 regardless of whether the route exists —
  route existence is only discoverable once authenticated. 403 is reserved for an
  authenticated principal lacking a required authority. Don't "fix" a 401 you expected
  to be a 404; check whether the request was authenticated first.
- **CSRF is enabled everywhere, including public endpoints** (register/login/etc.) —
  cookie-based session auth means every unsafe (POST/PUT/PATCH/DELETE) request needs a
  valid `X-XSRF-TOKEN` header, obtained from the `XSRF-TOKEN` cookie `CsrfCookieFilter`
  sets on every response. Never disable CSRF to make an endpoint "easier" to call.
- **Never point the Angular dev server straight at the backend's origin.** Use
  `proxy.conf.json` (already wired into `ng serve`). Angular's XSRF interceptor
  silently no-ops on cross-origin requests, so bypassing the proxy reintroduces a CSRF
  failure that only shows up in a real browser, not in curl/MockMvc-style tests.
- **Only a hash of a verification/reset token is ever persisted** (`TokenGenerator`) —
  never log or store the raw token. Both token types are one-time-use and expire; see
  `AuthProperties` for the centralized TTL/lockout configuration, never a scattered
  literal.
