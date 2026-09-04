# Legal Content Provisioning Plan

Post-MVP Milestone L1. **Mandatory section** — Canonical Phase 15 empirically proved
(a real, fresh, disposable PostgreSQL 18 instance, real Flyway V1→V48 migration) that
a brand-new production database starts with **zero published legal content**: 0
`PUBLISHED` `ProcedureVersion`/`RuleVersion`/`QuestionnaireVersion` rows, 0 users — only
the base procedure-identity seed (8 rows, from migration, never governed content
itself). This is by design (`CLAUDE.md`'s own standing rule: legal content is never
seeded via Flyway migration, only authored through the real Admin governance
workflow) — but it means **provisioning production's real legal content is a real,
unsolved operational step**, not something a fresh deploy does automatically.

## The problem, precisely

```text
Fresh PostgreSQL 18
        ↓
Flyway V1 → V48 (schema + reference data only)
        ↓
0 published procedures, 0 published Rules, 0 published Questionnaire, 0 Threshold
        ↓
??? ← this document
        ↓
4 published launch-ready procedures + Rules + a real minimum-wage Threshold +
verified Sources + reference data
```

## Options evaluated

### Option A — Restore a governed database snapshot

Take a real, full `pg_dump`/`pg_restore` (the exact, already-tested mechanism in
`DATABASE_BACKUP.md`/`DATABASE_RESTORE.md`) of a known-clean database state into the
production instance.

- **Advantages**: reuses infrastructure this project has already built and repeatedly
  drilled; preserves exact content, versions, sources, and audit history with zero
  reconstruction risk.
- **Risk, and why it is NOT safe as-is today**: the only real database this project has
  ever governed content in is the shared, long-lived local dev database — which, as of
  this milestone's own inspection, carries 36+ `TEST_*`-prefixed synthetic procedures
  and 300+ E2E/verification-created users, accumulated across every phase's own
  Playwright/curl verification runs (`PHASE_15_REPORT.md`'s data-quality findings).
  **A raw restore of this database into production is explicitly prohibited** (brief
  §70, this document's own mandatory rule) — it would leak test content and
  non-real user accounts into a real production launch.

### Option B — Controlled legal-content-only export/import

A new tool that exports only the governed legal-content tables (procedures, rule
versions, thresholds, questionnaires, official sources, and their provenance/FK
graph) while explicitly excluding `users`, E2E-created data, and anything not part of
the real governance record.

- **Advantages**: the durable, correct long-term answer — portable, explicit,
  excludes user data by construction rather than by a post-hoc cleanup pass.
- **Risk**: **this tooling does not currently exist** (confirmed by inspection this
  milestone — no content-only export/import capability anywhere in this codebase).
  Building it correctly means preserving `ProcedureVersion`/`RuleVersion`/
  `ThresholdVersion`/`QuestionnaireVersion` identity, every `OfficialSource`
  provenance link, and `AdminReview`/`AuditLog` history with valid (or intentionally
  pseudonymized) actor references — a real, non-trivial feature, not a quick script.
  **Not built in this milestone**, per its own explicit "do not build automatically in
  L1" instruction — recorded here as a real, scoped future launch-engineering task.

### Option C — Re-author through the real Admin governance workflow

Starting from the fresh, empty production database, a real ADMIN/CONTENT_EDITOR/
LEGAL_REVIEWER re-creates the 4 launch-ready procedures, their Rules, the minimum-wage
Threshold, and their Sources, through the actual production Admin UI — the same
draft → review → approve → publish workflow every real procedure in this project has
always gone through.

- **Advantages**: uses the real governance workflow exactly as designed; produces a
  100%-clean, 100%-real audit trail with no synthetic content risk whatsoever, by
  construction; needs no new tooling.
- **Risk**: slow (re-typing structured content that already exists once, correctly, in
  the dev database) and manually error-prone if not carefully cross-checked against
  the already-published dev content and the original sourced dossiers
  (`docs/procedures/<category>/<procedure>.md`).

## Recommended strategy for the first real launch

**A hybrid of Option A and Option C, in this order**, until Option B is built:

1. **Write and verify a real, explicit sanitization script** (a genuine, scoped
   engineering task for whoever performs the first real deployment — not attempted
   in this milestone) that, run against a *restored copy* of the dev database (never
   production directly), deletes: every `TEST_*`-prefixed procedure/rule/source and
   their versions; every user account that is not a real, intentionally-kept identity;
   and reviews `AuditLog`/`AdminReview` rows referencing deleted actors, converting
   them to the existing pseudonymous/null actor reference pattern this project's own
   governance model already supports where a submitter account should not be carried
   into production (`ARCHITECTURE.md` §8's admin-review model).
2. **Restore that sanitized snapshot into the production database** — reusing the
   already-tested restore mechanism, now operating on verified-clean data.
3. **Run the existing data-quality checks** (`infra/scripts/db-quality-check.sql`)
   against the result — the exact same script this project already used to prove a
   fresh database's schema correctness (Phase 15) — and require a real `0` on the
   "TEST-content leakage" and "orphan"/"self-approved" checks before the database is
   ever treated as production-ready.
4. **If the sanitization pass cannot be completed and verified with confidence before
   the intended launch date, fall back to Option C** (real re-authoring) for the
   smaller, well-understood set of 4 procedures — slower, but zero content-integrity
   risk.

Option B (proper export/import tooling) remains the recommended investment for any
*second* or later content-provisioning event (e.g. publishing the fifth procedure,
or a future disaster-recovery restore) — tracked in `POST_MVP_ROADMAP.md`.

## What production must end up with

```text
4 published launch-ready procedures (PESEL, address registration/meldunek,
EU citizen residence registration, temporary residence and work)
        +
6 published production Rules targeting them
        +
1 real minimum-wage Threshold (current effective value/source)
        +
Verified OfficialSource rows backing every one of the above
        +
Reference data (countries, regions, cities, districts, authorities, offices)
        +
0 real users (except the one intentional bootstrap ADMIN — see below)
        +
0 TEST_* content
        +
0 E2E-created users
```

**Temporary residence for studies remains excluded** — its own source-verification
gate is unchanged by this milestone; publishing it is a separate, deliberate
legal-content governance action (`LEGAL_CONTENT_MONITORING.md`), never a side effect
of the provisioning process above.

## Validation gate (mandatory before declaring production content-ready)

Every check in `infra/scripts/db-quality-check.sql` must pass with a genuinely
empty/zero result on every "must be 0 rows" check — exactly the standard this project
already applied to the fresh-schema pass in Phase 15, now applied to the *content*
provisioning result specifically.

## Bootstrap Admin vs. content actors

The production database's first real account is the one intentional
`AdminBootstrapRunner`-created `ADMIN` (see `DEPLOYMENT.md` step 8,
`PRODUCTION_CONFIGURATION_PLAN.md`). **Do not create old development Admin/reviewer
accounts in production merely to satisfy a foreign key** on imported governance
history — where the sanitization pass (step 1 above) finds an `AdminReview`/
`AuditLog` row referencing a development-only actor that should not exist in
production, use this project's own existing nullable/pseudonymous actor-reference
support (`ARCHITECTURE.md` §8) rather than fabricating a matching account.
