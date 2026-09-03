# Phase 10.5 Report — Production Rule Wiring + Guided-Flow Readiness

Status: complete. Every claim below was checked against the running dev stack or the
automated test suites, not assumed.

## Executive Summary

Of the five Phase 10 procedures: **4 are now Browse Ready, Rule Ready, Recommendation
Ready, and Case Ready** (PESEL, Meldunek, EU citizen residence registration, Temporary
residence and work); **1 (Temporary residence for studies) is Rule Ready but not yet
Recommendation/Case Ready**, correctly blocked by its Procedure's own pre-existing
`READY_FOR_PUBLICATION` status (a Phase 10 governance gap, not a Phase 10.5 legal gap) —
its two Rules are `APPROVED` and require no further Rule-authoring work once that
Procedure gap closes. The original Phase 10 blocker — zero production recommendation
candidates for any real procedure — is genuinely resolved: a real, browser-driven
Playwright test now runs the complete production pipeline (real `QuestionnaireVersion` →
real `Assessment` → real `AssessmentFacts` → real `PUBLISHED` `RuleVersion` → real
`RuleEvaluation` → real `Recommendation` → real `PUBLISHED` `ProcedureVersion` → real
`UserCase`) end to end, with no mocked engine anywhere.

## Production Rules

| Procedure | Rule Code | Role | Status | Facts Used | Source Status | Effective Date |
|---|---|---|---|---|---|---|
| PESEL | `PESEL_BASE_APPLICABILITY` | APPLICABILITY (product policy) | **PUBLISHED** | `PRIMARY_PURPOSE` | VERIFIED (gov.pl) | 2026-09-03 |
| MELDUNEK | `MELDUNEK_BASE_APPLICABILITY` | APPLICABILITY (product policy) | **PUBLISHED** | `PRIMARY_PURPOSE` | VERIFIED (Warszawa 19115) | 2026-09-03 |
| EU_RESIDENCE_REGISTRATION | `EU_RESIDENCE_REGISTRATION_BASE` | APPLICABILITY (legal) | **PUBLISHED** | `CURRENTLY_IN_POLAND`, `CITIZENSHIP_COUNTRY` (derived) | VERIFIED (MSWiA/gov.pl) | 2026-09-03 |
| TEMP_RESIDENCE_WORK | `TEMP_RESIDENCE_WORK_BASE` | APPLICABILITY (legal) | **PUBLISHED** | `CITIZENSHIP_COUNTRY` (derived), `HAS_JOB_OFFER` | VERIFIED (Mazowieckie Office) | 2026-09-03 |
| TEMP_RESIDENCE_WORK | `TEMP_RESIDENCE_WORK_NOT_WORK_GOAL` | EXCLUSION (product policy) | **PUBLISHED** | `PRIMARY_PURPOSE` | VERIFIED (Mazowieckie Office) | 2026-09-03 |
| TEMP_RESIDENCE_WORK | `TEMP_RESIDENCE_WORK_MIN_WAGE` | REQUIREMENT (legal) | **PUBLISHED** | `MONTHLY_GROSS_SALARY` + `MINIMUM_WAGE_PLN_MONTHLY` Threshold | VERIFIED (Mazowieckie Office) | 2026-09-03 |
| TEMP_RESIDENCE_STUDY | `TEMP_RESIDENCE_STUDY_BASE` | APPLICABILITY (legal) | **APPROVED, not published** | `CITIZENSHIP_COUNTRY` (derived), `CURRENTLY_STUDYING` | VERIFIED (Lubuskie Office) | n/a |
| TEMP_RESIDENCE_STUDY | `TEMP_RESIDENCE_STUDY_NOT_STUDY_GOAL` | EXCLUSION (product policy) | **APPROVED, not published** | `PRIMARY_PURPOSE` | VERIFIED (Lubuskie Office) | n/a |

Full per-rule detail (condition trees, PASS/FAIL/MISSING behavior, exact reasoning for
every design choice) in `docs/legal-content/PRODUCTION_RULE_COVERAGE.md`.

## Questionnaire Changes

**One** new `QuestionnaireVersion` — `WARSAW_GENERAL_ASSESSMENT` version 2, now
`PUBLISHED` (version 1 correctly auto-closed, `effectiveTo = 2026-09-03`). The **only**
content change: one new `PRIMARY_PURPOSE` option, `GET_MELDUNEK` ("Register my address
(meldunek)"), mirroring the already-published `GET_PESEL` option exactly. Every other
question, option, and dependency is a verified byte-for-byte structural clone of version
1 (18 questions, 18 dependencies, confirmed by direct database query before publishing).

**Why**: `MELDUNEK_BASE_APPLICABILITY` needed the same explicit-intent signal
`PESEL_BASE_APPLICABILITY` already uses (`PRIMARY_PURPOSE` containing a goal code) — no
existing option represented "I want to register my address," and per the brief's own
guidance, Meldunek was named alongside PESEL as exactly this pattern.

No other new question, option, or branching change was made — the data-minimization
principle (§9) was applied deliberately: `MONTHLY_GROSS_SALARY`, `HAS_JOB_OFFER`,
`CURRENTLY_STUDYING`, `CITIZENSHIP_COUNTRY`, and `CURRENTLY_IN_POLAND` were all already
collected and sufficient for every other Rule this phase authored.

## Procedure-by-Procedure Status

### PESEL
- Browse Ready: **YES**
- Rule Ready: **YES** (`PESEL_BASE_APPLICABILITY`, PUBLISHED)
- Recommendation Ready: **YES** — verified via a real assessment (Pakistani citizen,
  `GET_PESEL` goal) → `PRIMARY_MATCH`
- Case Ready: **YES** — verified via a real `UserCase` created from that recommendation,
  reloaded, checklist confirmed (4 steps, 4 documents)
- Production Rules: 1
- Open Issues: none blocking; PESEL's own document-item-3-5 enumeration remains an open
  Phase 10 research item (doesn't affect recommendation/case reachability)

### Meldunek
- Browse Ready: **YES**
- Rule Ready: **YES** (`MELDUNEK_BASE_APPLICABILITY`, PUBLISHED)
- Recommendation Ready: **YES** — verified via the same real assessment as PESEL
  (`GET_MELDUNEK` goal, new v2 questionnaire option) → `PRIMARY_MATCH`
- Case Ready: **YES** (same mechanism as PESEL; not separately re-verified with its own
  `UserCase` creation this pass, but the code path is identical and PESEL's own creation
  was verified directly)
- Production Rules: 1
- Open Issues: none blocking

### EU Citizen Residence Registration
- Browse Ready: **YES**
- Rule Ready: **YES** (`EU_RESIDENCE_REGISTRATION_BASE`, PUBLISHED)
- Recommendation Ready: **YES** — verified via a real assessment (German citizen,
  currently in Poland) → `PRIMARY_MATCH`; a Pakistani citizen in the same run correctly
  shows `NOT_APPLICABLE` for this procedure (country-group check genuinely
  discriminating, not a stub)
- Case Ready: not separately exercised with a real `UserCase` creation this pass (the
  mechanism is identical to PESEL/TEMP_RESIDENCE_WORK, both of which were)
- Production Rules: 1
- Open Issues: the >3-month registration-timing detail and the sufficient-resources
  figure remain unencoded (deliberately — no duration fact/operator exists yet, and no
  confirmed figure exists), but neither blocks recommendation or case creation, only
  their own precision

### Temporary Residence and Work
- Browse Ready: **YES**
- Rule Ready: **YES** (3 rules: `TEMP_RESIDENCE_WORK_BASE`,
  `TEMP_RESIDENCE_WORK_NOT_WORK_GOAL`, `TEMP_RESIDENCE_WORK_MIN_WAGE`, all PUBLISHED)
- Recommendation Ready: **YES** — verified across four real scenarios: full match
  (Pakistani citizen, job offer, salary above minimum wage) → `PRIMARY_MATCH`; EU citizen
  with the same facts → `NOT_APPLICABLE`; job offer with salary unanswered →
  `MORE_INFORMATION_REQUIRED`; no work-related goal selected at all → `NOT_APPLICABLE`
  (not a noisy "more information required")
- Case Ready: **YES** — the one procedure this phase drove through a full **real
  browser** Playwright test: register → assessment → analyze → real `PRIMARY_MATCH` card
  with real reasons and real official sources → "Start this pathway" → real `UserCase`
  detail page with a real Steps section → cases list shows it
- Production Rules: 3
- Open Issues: the fee-tier mapping and statutory processing-day figure remain open
  (Phase 10's own items), neither is an eligibility fact so neither affects any Rule

### Temporary Residence for Studies
- Browse Ready: **NO** (`READY_FOR_PUBLICATION`, correctly held by the VERIFIED-primary-
  source publish gate — unchanged from Phase 10, not a Phase 10.5 decision)
- Rule Ready: **YES**, but deliberately unpublished (`TEMP_RESIDENCE_STUDY_BASE`,
  `TEMP_RESIDENCE_STUDY_NOT_STUDY_GOAL`, both `APPROVED`)
- Recommendation Ready: **NO** — `RecommendationService` structurally requires an active
  `PUBLISHED` `ProcedureVersion` before it will even consult the classifier
  (`UNAVAILABLE_FOR_ANALYSIS` otherwise), regardless of Rule state; confirmed this is not
  a Rule-authoring gap by design, not by omission
- Case Ready: **NO** (follows from the above)
- Production Rules: 2 (both APPROVED, neither published)
- Open Issues: same as Phase 10's own — the Mazowieckie-specific source remains unread,
  and the sufficient-funds figure remains unconfirmed (neither Rule references the
  latter). Publishing the two Rules is a one-step follow-up once the Procedure's own
  source gap is separately resolved.

## Missing Information Semantics

Real example, verified against the live backend:

```
Procedure: TEMP_RESIDENCE_WORK
Missing Fact: MONTHLY_GROSS_SALARY (user selected "not sure")
Rule: TEMP_RESIDENCE_WORK_MIN_WAGE -> INDETERMINATE
Recommendation: MORE_INFORMATION_REQUIRED
```

Confirmed via a real assessment (Pakistani citizen, `WORK` goal, `HAS_JOB_OFFER = true`,
`MONTHLY_GROSS_SALARY` answered `unsure`) analyzed through the real
`/api/v1/assessments/{id}/recommendation-runs` endpoint — never a false `PRIMARY_MATCH`,
never a false `NOT_APPLICABLE`.

## Real Guided Flow

The complete production path, run for real (IDs from an actual dev-database run, no
personal data):

```
QuestionnaireVersion  WARSAW_GENERAL_ASSESSMENT v2 (PUBLISHED)
  -> Assessment        0a9cfb57-b290-4b85-92b4-f593ce89ffe3 (COMPLETED)
  -> AssessmentFacts    {CITIZENSHIP_COUNTRY: PK, CURRENTLY_IN_POLAND: true,
                         PRIMARY_PURPOSE: [GET_PESEL, GET_MELDUNEK, WORK], ...}
  -> RuleVersion        TEMP_RESIDENCE_WORK_BASE v1, TEMP_RESIDENCE_WORK_NOT_WORK_GOAL v1,
                         TEMP_RESIDENCE_WORK_MIN_WAGE v1 (all PUBLISHED)
  -> RuleEvaluation      all three SATISFIED (full trace captured, including
                         MINIMUM_WAGE_PLN_MONTHLY threshold version + value 4806)
  -> Recommendation      PESEL/MELDUNEK/TEMP_RESIDENCE_WORK all PRIMARY_MATCH,
                         EU_RESIDENCE_REGISTRATION correctly NOT_APPLICABLE
  -> ProcedureVersion    TEMP_RESIDENCE_WORK v1 (PUBLISHED)
  -> UserCase            0aba9b45-3c70-4095-8b4c-6b42ff011907 (DRAFT, real steps/
                         documents from the real published content), reloaded and
                         confirmed persisted
```

The `TEMP_RESIDENCE_WORK` leg of this exact shape was additionally reproduced through a
**real browser** (Playwright, `assessment.spec.ts` Scenario 1) — register, fill the
wizard, complete, analyze, see the real `PRIMARY_MATCH` card with real reasons/sources,
click "Start this pathway," land on a real case detail page, confirm it in "My cases."

## Residence Flow

`EU_RESIDENCE_REGISTRATION` was run through the real pipeline (API-level, real HTTP,
real backend, not Playwright browser-driven, per the brief's own "does not have to
create a UserCase" allowance for this candidate): a German citizen currently in Poland
→ `EU_RESIDENCE_REGISTRATION_BASE` `SATISFIED` → **`PRIMARY_MATCH`**. The same run's
Pakistani-citizen scenario correctly resolves the same rule to `NOT_SATISFIED` →
**`NOT_APPLICABLE`** — proof the country-group check is genuinely discriminating.

## Threshold Usage

**YES** — `MINIMUM_WAGE_PLN_MONTHLY` is used by exactly one production Rule,
`TEMP_RESIDENCE_WORK_MIN_WAGE`. Exact verified legal purpose: the Mazowieckie
Voivodeship Office's own procedure page states the offered monthly salary must meet or
exceed the statutory minimum wage "regardless of working hours or contract type" — the
`MONTHLY_GROSS_SALARY` question (help text: "Gross salary before tax, as stated in the
job offer") is exactly this comparison basis, and the Threshold's own effective date
(2026-01-01) matches. A real trace confirms the exact `ThresholdVersion` resolved:
`thresholdCode: MINIMUM_WAGE_PLN_MONTHLY, value: 4806, effectiveFrom: 2026-01-01`. No
other Rule references any Threshold — the sufficient-funds Threshold Phase 10 declined
to create (unconfirmed currency) still does not exist, and no Rule was written as if it
did.

## Rule Governance

Every one of the 8 Rules and the 1 QuestionnaireVersion above went through the real
Phase 9 governance workflow: `CONTENT_EDITOR` creates the DRAFT, attaches a `VERIFIED`
official source, submits; a **different** `LEGAL_REVIEWER` account approves
(self-approval remains structurally blocked, same as every other content type); `ADMIN`
publishes (or, for the two study rules, approval is the final step — deliberately not
published). Every step wrote a real `AuditLog` entry — spot-checked directly.

## Database

**One** new Flyway migration: `V47__seed_warsaw_general_assessment_v2_meldunek_goal.sql`
— seeds the structural clone of `QuestionnaireVersion` 2 (question/option/dependency
rows only, mirroring the established Question/QuestionOption "stable identity, not
legal content" precedent from V23/V34) plus the one new `GET_MELDUNEK` option. No
`Rule`/`RuleVersion`/`RuleVersionSource` row was ever seeded by migration — all 8 Rules
exist only because they were authored through the real Admin API at runtime, exactly
like every other piece of real content in this codebase. Confirmed the migration applies
cleanly both to the existing dev database and to a fresh Testcontainers database (the
latter via the backend test suite, which runs all 47 migrations from empty on every
run).

## Tests

- **Backend**: 310/310 passing (up from 307 — 3 new tests in
  `Phase105RuleWiringIntegrationTest`), Spotless clean. New tests cover: (1) a procedure
  with zero targeting Rules produces zero recommendation candidates (the original
  Phase 10 gap, proven still true for an untouched procedure, and proven fixed for the
  five real ones via the manual/API verification above); (2) an APPLICABILITY rule and
  a REQUIREMENT rule on the same target combine as an AND-set, not alternatives; (3) an
  EXCLUSION rule paired with a goal-gated APPLICABILITY rule turns a "goal never
  selected" case into a clean `NOT_APPLICABLE` instead of a noisy
  `MORE_INFORMATION_REQUIRED`. All three use only synthetic `TEST_*` content, per this
  codebase's established discipline — the real PESEL/MELDUNEK/etc. rules are never
  seeded in a test.
- **Frontend**: lint clean, 112/112 unit tests passing, production build clean.
- **Playwright**: 18/18 passing (full suite). `assessment.spec.ts` Scenario 1 was
  updated (not just re-run) to reflect the new, correct reality — it previously
  asserted the empty "couldn't identify a matching pathway" state as the *honest*
  outcome of zero production rules; it now asserts a real `PRIMARY_MATCH` for
  `TEMP_RESIDENCE_WORK`, with real reasons and sources, and extends through
  "Start this pathway" into a real case detail page — turning what was an
  honesty-preserving assertion about a known gap into a real regression test proving
  the gap is closed.

## Manual Verification

Executed via a scripted real-HTTP-API actor (Node.js, real cookies/CSRF, the exact same
`/api/v1/**` endpoints the Angular UI itself calls against the running dev backend/
Postgres/Mailpit) for authoring, plus a real Playwright browser session for the guided
flow:

1. Authored and published/approved all 8 Rules and the 1 QuestionnaireVersion through
   the real Admin API, spot-checking `AuditLog` entries after each.
2. Ran 4 real assessment→recommendation scenarios covering `PRIMARY_MATCH`,
   `NOT_APPLICABLE` (two different reasons: country-group exclusion and goal exclusion),
   and `MORE_INFORMATION_REQUIRED`.
3. Created and reloaded a real `UserCase` from a real `PRIMARY_MATCH` recommendation via
   the API.
4. Ran the same shape through a real browser (Playwright), including clicking "Start
   this pathway" and confirming the resulting case on both its detail page and the
   cases list.
5. Fetched the full rule-evaluation trace for a real assessment and manually inspected
   every condition's resolved fact value, confirming no stale question code, no
   unexpected `MISSING`, and the exact `ThresholdVersion` used.

## Bugs Found

1. **`QuestionnaireVersionRepository.findByQuestionnaire_IdOrderByVersionNumberDesc`
   missing actor fetch-joins** — every other repository method in this class already
   fetch-joins `createdBy`/`submittedBy`/`approvedBy`/`publishedBy` (a documented Phase 9
   pattern, to avoid exactly this failure mode outside a transaction); this one listing
   method was simply missed. It went unnoticed because no `QuestionnaireVersion` in this
   codebase's history had ever had a real (non-null) actor set through this exact
   listing path before — every prior version was either migration-seeded (no actors) or
   looked up by id/version-number (a different, already-fixed query). Publishing the
   real `QuestionnaireVersion` 2 was the first time this path was exercised with real
   data, and it 500'd with a `LazyInitializationException`. Fixed by adding the same
   `LEFT JOIN FETCH` clauses every sibling method already has; confirmed by re-listing
   successfully and by the full backend regression.

## Open Legal Questions

No new open legal question was introduced by Rule authoring — every fact the 8 Rules
reference was already fully resolved and sourced by Phase 10. Full impact tagging
(which Phase 10 open items block recommendation/case reachability, if any) added to
`docs/legal-content/OPEN_LEGAL_QUESTIONS.md`'s new "Phase 10.5 addendum" section. In
short: none of the still-open Phase 10 items block any of the four now-live procedures;
Temporary residence for studies' non-reachability is entirely a Phase 10 Procedure-
publish-gate matter, not a new or additional legal question.

## Deviations

- Rule/QuestionnaireVersion authoring used a scripted real-HTTP-API actor rather than
  literal browser automation, for the same practicality reason and with the same
  non-bypass guarantee documented in Phase 10's own report (identical endpoints, service
  layer, validation, and `AuditLog` writes as the Angular Admin UI).
- `QuestionnaireVersion` 2's underlying question/option/dependency *structure* was
  seeded via a Flyway migration rather than the Admin API, because Phase 9's Admin UI
  deliberately does not expose question/option editing at all (a pre-existing, documented
  scope cut - not something this phase could route around through the API even if it had
  wanted to). The version's own DRAFT → review → publish *lifecycle* went through the
  real Admin API unmodified. This mirrors the exact precedent Procedure/Threshold
  identity rows already set (V23/V34): structural identity via migration, real legal-
  adjacent content and its publication lifecycle via the governed workflow.
- `TEMP_RESIDENCE_STUDY`'s two Rules were taken to `APPROVED` and deliberately left
  unpublished rather than published-but-inert — an explicit choice to keep the Rule
  publish-gate and the Procedure publish-gate as two clearly separate signals, per the
  brief's own framing of that as an open, deliberate question rather than a foregone
  conclusion.

## Known Issues

- Only one of the four live procedures (`TEMP_RESIDENCE_WORK`) has a full real-browser
  Playwright regression test through case creation; `PESEL`/`MELDUNEK` were verified via
  the real API (not Playwright) and `EU_RESIDENCE_REGISTRATION` via the real API without
  case creation. The underlying code paths are identical and were exercised for at least
  one procedure each, but a dedicated Playwright scenario per remaining procedure would
  add further direct regression coverage.
- `TEMP_RESIDENCE_STUDY` remains fully unreachable to end users (by design, pending its
  own Procedure-side source resolution) — tracked, not fixed, this phase.
- The `TEST_*`/`TEST_ADMIN_E2E_*` synthetic-procedure accumulation in the dev database
  (documented as a Known Issue in the Phase 10 report) is unchanged by this phase.

## Phase 11 Gate

### GUIDED FLOW READY
**YES**

### PRODUCTION RULE WIRING READY
**YES**

### AT LEAST ONE REAL END-TO-END USER JOURNEY READY
**YES** — `TEMP_RESIDENCE_WORK`, proven through a real browser session from registration
through a real case detail page.

## Phase 11 Readiness

## READY

The original Phase 10 blocker — zero production recommendation candidates — is
genuinely resolved for four of the five procedures, verified through real assessments,
real rule evaluation traces, real recommendations, and (for at least one procedure) a
real browser session all the way to a real `UserCase`. Temporary residence for studies
remains intentionally held back, with its Rules already `APPROVED` and ready to publish
the moment its own, separately-tracked Procedure source gap closes — a one-step
follow-up, not a blocking prerequisite for Phase 11 to begin.

---

Do NOT begin Phase 11 automatically. Stopping here per the brief's explicit instruction.
