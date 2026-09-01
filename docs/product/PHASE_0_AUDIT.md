# Phase 0 Final Audit — Foreigner Warsaw

Date: 2026-09-01
Auditor: Claude Code (this session)

## Completed Phase 0 files

- [README.md](../../README.md)
- [CLAUDE.md](../../CLAUDE.md)
- [docs/product/PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md)
- [docs/product/PROCEDURE_CATALOGUE.md](PROCEDURE_CATALOGUE.md)
- [docs/product/ASSESSMENT_DECISION_TREE.md](ASSESSMENT_DECISION_TREE.md)
- [docs/product/IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)
- [docs/architecture/ARCHITECTURE.md](../architecture/ARCHITECTURE.md)
- [docs/architecture/ADR/ADR-001-modular-monolith.md](../architecture/ADR/ADR-001-modular-monolith.md)
- [docs/architecture/ADR/ADR-002-postgresql.md](../architecture/ADR/ADR-002-postgresql.md)
- [docs/architecture/ADR/ADR-003-rules-engine.md](../architecture/ADR/ADR-003-rules-engine.md)
- [docs/architecture/ADR/ADR-004-versioned-legal-content.md](../architecture/ADR/ADR-004-versioned-legal-content.md)
- [docs/architecture/ADR/ADR-005-authentication-strategy.md](../architecture/ADR/ADR-005-authentication-strategy.md)
- [docs/database/DATABASE.md](../database/DATABASE.md)
- Empty scaffold folders: `backend/`, `frontend/`, `infra/docker/`, `infra/production/`,
  `docs/procedures/{residence,eu-free-movement,administrative,driving}/`,
  `docs/legal-sources/`, `docs/api/`, `scripts/`, `.github/workflows/`

**Technology consistency check**: grepped every doc for version strings — Java 25,
Spring Boot 4.1.x, PostgreSQL 18, Angular 22 are stated identically everywhere they
appear (README, ARCHITECTURE.md, DATABASE.md, IMPLEMENTATION_PLAN.md, ADR-002). No
contradictions found; no version was silently downgraded.

## Major architecture decisions (15)

1. **Modular monolith**, package-by-feature — not microservices (ADR-001).
2. **PostgreSQL 18 + Flyway**, `ddl-auto=validate` everywhere, schema changes only via
   migration files (ADR-002).
3. **Deterministic rules engine**, never an LLM, decides eligibility (ADR-003).
4. **Legal content is append-only versioned data** — identity + version table pairs for
   Procedure/Rule/DocumentRequirement/Fee/Threshold; a "change" is a new row, never an
   `UPDATE` (ADR-004).
5. **Cookie-based session auth**, not `localStorage` tokens (ADR-005).
6. **JSONB condition tree** for `RuleVersion` (not a normalized `RuleCondition` table),
   with a narrow `RuleThresholdReference` companion table specifically to keep
   "what references this threshold" queryable without parsing JSON.
7. **The Active-Version Predicate** — one reusable filter
   (`status='PUBLISHED' AND effective_from<=date AND (effective_to IS NULL OR
   effective_to>date)`) is the single mechanism behind both publication safety (draft
   content can never leak) and temporal evaluation (historical cases replay exactly).
8. **Jurisdiction (legal scope) is a separate concept from Geography** (`Country` /
   `Region` / `City` / `District`) — Procedure/Rule/Authority reference Jurisdiction;
   Office/Authority reference Geography. This is what lets a Kraków rollout be a data
   change.
9. **`Region`, not a table literally named `Voivodeship`** — a deliberate naming
   deviation from the brief, justified in DATABASE.md §2, to keep Poland's
   administrative-division vocabulary out of the schema's structure.
10. **UUID primary keys plus a stable human-readable `code`** on every
    rule/URL-referenced entity.
11. **`UserCase` pins an exact `ProcedureVersion`** and records every version ID used
    (`UserCaseRequirementSnapshot`) — republishing a procedure never silently changes an
    existing case; the user sees an explicit, opt-in diff.
12. **`Recommendation` is a computed cache, not versioned legal history** — deliberately
    replaced (not append-only) when an assessment is re-evaluated, in contrast to
    decision 4. Named explicitly so the two patterns aren't confused.
13. **No `QuestionnaireVersion` lifecycle** — considered and rejected; `AssessmentAnswer`
    already snapshots what was asked/answered, so question-wording iteration stays cheap
    without a full DRAFT→PUBLISHED workflow that legal content genuinely needs.
14. **PostgreSQL exclusion constraints** (`EXCLUDE USING gist`, `btree_gist`) enforce
    non-overlapping published effective-date ranges at the database level for
    `ProcedureVersion`, `RuleVersion`, and `ThresholdVersion` — not just application
    logic.
15. **Anonymous assessment start** — `Assessment.user_id` is nullable so "Help me
    choose" can begin before registration, claimed on signup/login.

## MVP recommendation

The 8-procedure MVP list is the right *target for the roadmap* but I'd recommend
**against launching all 8 simultaneously as the first live release**. Two of the eight
carry unresolved data questions surfaced during this pass, not just "needs a `VERIFIED`
stamp":

- **EU Blue Card**: the salary-threshold effective date and announcement date
  disagree in secondary sources (PROCEDURE_CATALOGUE.md flags this explicitly) — this
  is exactly the kind of figure that becomes a `ThresholdVersion` and a wrong one here
  has real consequences.
- **Family reunification (spouse of Polish citizen)**: the "stable and regular income
  sufficient" test has no identified figure — it may genuinely be case-by-case, which
  changes how it's modeled (a `MORE_INFORMATION_REQUIRED`-producing condition, not a
  `Threshold`) rather than just needing verification.
- **Driving licence exchange** is flagged as the single most country-sensitive
  procedure in the catalogue (per-country exam requirements) — encoding it for "every
  country" on day one risks becoming its own research project.

**Recommended first-release subset (5 of the 8):** PESEL, Meldunek, EU citizen
residence registration, Temporary residence and work, Temporary residence for studies —
these have the cleanest sourcing and the least open legal ambiguity. Fast-follow with
EU Blue Card and Family Reunification once Phase 10.1's legal review resolves the two
open questions above, and ship driving-licence exchange initially for a handful of the
most common issuing countries/regions (EU/EEA/Switzerland/UK, plus 3–5 other high-volume
countries) rather than attempting full global coverage immediately. This doesn't change
IMPLEMENTATION_PLAN.md's phase structure — it only affects which 10.x tasks gate the
first production deploy (13.7) versus which follow shortly after.

## Biggest risks (ranked)

1. **Legal-content correctness** — every MVP source is still `DRAFT`; two procedures
   have open data questions, not just unverified ones (see above).
2. **Active-Version Predicate / rules-engine correctness** — the single piece of logic
   every other feature depends on; flagged `High` risk in IMPLEMENTATION_PLAN.md tasks
   4.9 and 6.4 specifically because an off-by-one here either leaks draft content or
   hides published content.
3. **Versioning-workflow correctness** — exclusion constraints and JSONB condition
   trees are less-familiar Postgres/JPA territory for most teams; getting the
   snapshot/diff mechanics (`UserCaseRequirementSnapshot`) wrong would silently break
   the "requirements changed" guarantee that's central to user trust.
4. **Scope size vs. delivery capacity** — ~150 implementation tasks across 9 phases
   before any real procedure content is even encoded (Phase 10). Discipline against
   scope creep into the catalogue's much larger `NOT_STARTED` list is required.
5. **Country-rule complexity growth** — driving-licence recognition and document
   legalisation rules are genuinely per-country; the data model supports this, but the
   research burden scales with every country added.
6. **Admin publishing mistakes** — mitigated by publish validation and mandatory human
   approval, but the Admin panel (Phase 9) is a new, powerful surface the moment it
   exists, and its own test coverage (9.8) needs to be taken seriously, not treated as
   an afterthought.

## Open questions

Only the one that actually changes near-term work:

1. **Is the narrowed 5-procedure first-release recommendation above acceptable**, or is
   launching all 8 together a hard product requirement? This changes which Phase 10
   tasks gate Phase 13's production deploy. Everything else in this audit either has a
   documented default (and rationale) already applied, or doesn't block engineering
   work that's queued before it would matter (e.g. production hosting provider, Phase
   13, is far enough out that it doesn't need an answer today).

## Phase 1 readiness

**READY.**

Phase 1 (repository/infrastructure scaffolding) has no dependency on unresolved legal
content — it's Spring Boot, Angular, Docker Compose, Flyway baseline, health checks, and
CI, none of which touch the Blue Card threshold or any other open data question above.
Those questions are correctly scoped to Phase 10 (task 10.1 specifically gates them
before anything publishes to production) and don't block Phases 1–9's engineering work.
