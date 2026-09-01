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
7. [docs/architecture/ADR/](docs/architecture/ADR/) — the "why" behind the five foundational decisions

**Current status: Phase 1 (repository/infrastructure) complete.** No business features
exist yet (auth, procedures, questionnaire, rules engine, cases, admin — all later
phases per IMPLEMENTATION_PLAN.md). Work through IMPLEMENTATION_PLAN.md's tasks in
order starting at Phase 2.

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
cd frontend && npx playwright install chromium && npm run e2e   # backend should be running too
```

No Maven install required — `backend/mvnw`/`mvnw.cmd` bootstrap the pinned Maven
version themselves. `backend/target` occasionally hits a transient Windows file-lock on
`mvnw clean` right after a build; `rm -rf backend/target` and retry, it's not a code
issue.

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
