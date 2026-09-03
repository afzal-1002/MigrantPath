# Production Rule Coverage — Phase 10.5

Status: complete. Every row below is a real `Rule`/`RuleVersion` authored through the real
Admin API (`CONTENT_EDITOR` → DRAFT → VALIDATE → attach `VERIFIED` source → SUBMIT →
`LEGAL_REVIEWER` APPROVE → `ADMIN` PUBLISH), never a migration, never a direct SQL
insert — mirroring exactly the discipline Phase 10 established for legal content, and
verified against the running dev database and a real end-to-end guided-flow test (see
`docs/product/PHASE_10_5_REPORT.md`).

## Rule coverage matrix

| Procedure | Rule code | Role | Legal/product fact represented | Source | Required facts | Question code(s) | Availability | Missing question? | PASS behavior | FAIL behavior | MISSING behavior | Recommendation effect | Publication |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| PESEL | `PESEL_BASE_APPLICABILITY` | APPLICABILITY (**product policy**, not legal entitlement) | User explicitly said they want a PESEL number | gov.pl (PESEL, VERIFIED) | `PRIMARY_PURPOSE` contains `GET_PESEL` | `PRIMARY_PURPOSE` | AVAILABLE (always visible, required) | No | Only required rule SATISFIED | Only required rule NOT_SATISFIED | n/a — `PRIMARY_PURPOSE` is always answered for a completed assessment | SATISFIED → `PRIMARY_MATCH`; NOT_SATISFIED → `NOT_APPLICABLE` | **PUBLISHED** |
| MELDUNEK | `MELDUNEK_BASE_APPLICABILITY` | APPLICABILITY (**product policy**) | User explicitly said they want to register their address | Warszawa 19115 (Meldunek, VERIFIED) | `PRIMARY_PURPOSE` contains `GET_MELDUNEK` | `PRIMARY_PURPOSE` | AVAILABLE (new option, `QuestionnaireVersion` 2 — see below) | Yes, resolved this phase | Only required rule SATISFIED | Only required rule NOT_SATISFIED | n/a, same as PESEL | SATISFIED → `PRIMARY_MATCH`; NOT_SATISFIED → `NOT_APPLICABLE` | **PUBLISHED** |
| EU_RESIDENCE_REGISTRATION | `EU_RESIDENCE_REGISTRATION_BASE` | APPLICABILITY (**genuine legal fact**) | Free-movement registration applies to an EU/EEA/Swiss citizen currently in Poland | MSWiA (gov.pl, VERIFIED) | `CURRENTLY_IN_POLAND` = true AND `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP` = false | `CURRENTLY_IN_POLAND`, `CITIZENSHIP_COUNTRY` (derived fact) | AVAILABLE (both always visible, required) | No | Both PASS | Either FAILs (e.g. non-EU/EEA/Swiss citizen, or not in Poland) | Citizenship/presence unanswered (only possible pre-completion) | SATISFIED → `PRIMARY_MATCH`; NOT_SATISFIED → `NOT_APPLICABLE` | **PUBLISHED** |
| TEMP_RESIDENCE_WORK | `TEMP_RESIDENCE_WORK_BASE` | APPLICABILITY (**genuine legal fact**) | The uniform work permit applies to a third-country national with an actual job offer | Mazowieckie Voivodeship Office (VERIFIED) | `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP` = true AND `HAS_JOB_OFFER` = true | `CITIZENSHIP_COUNTRY` (derived), `HAS_JOB_OFFER` | AVAILABLE (`HAS_JOB_OFFER` gated behind `PRIMARY_PURPOSE` containing `WORK`/`HIGHLY_QUALIFIED_WORK`) | No | Both PASS | Citizenship is EU/EEA/Swiss (FAIL) | `HAS_JOB_OFFER` never asked (goal not selected) | SATISFIED → candidate; NOT_SATISFIED → `NOT_APPLICABLE`; MISSING → `MORE_INFORMATION_REQUIRED` **unless** the exclusion below also fires | **PUBLISHED** |
| TEMP_RESIDENCE_WORK | `TEMP_RESIDENCE_WORK_NOT_WORK_GOAL` | EXCLUSION (**product policy**, prevents a noisy result) | The user never selected a work-related goal at all | Mazowieckie Voivodeship Office (VERIFIED) | `PRIMARY_PURPOSE` does not contain `WORK` AND does not contain `HIGHLY_QUALIFIED_WORK` | `PRIMARY_PURPOSE` | AVAILABLE (always visible, required) | No | Exclusion SATISFIED (goal not selected) | Exclusion NOT_SATISFIED (a work goal was selected) | n/a, `PRIMARY_PURPOSE` always answered | SATISFIED → `NOT_APPLICABLE` (wins over any MISSING elsewhere) | **PUBLISHED** |
| TEMP_RESIDENCE_WORK | `TEMP_RESIDENCE_WORK_MIN_WAGE` | REQUIREMENT (**genuine legal fact**) | The offered salary must meet the statutory minimum wage | Mazowieckie Voivodeship Office (VERIFIED) — same source as the `MINIMUM_WAGE_PLN_MONTHLY` Threshold itself | `MONTHLY_GROSS_SALARY` ≥ `MINIMUM_WAGE_PLN_MONTHLY` (Threshold, 4,806 PLN, effective 2026-01-01) | `MONTHLY_GROSS_SALARY` | AVAILABLE (gated behind `HAS_JOB_OFFER` = true, not required itself) | No | Salary ≥ threshold | Salary < threshold | Salary unanswered | SATISFIED → candidate stays match-eligible; NOT_SATISFIED → `NOT_APPLICABLE`; MISSING → `MORE_INFORMATION_REQUIRED` | **PUBLISHED** |
| TEMP_RESIDENCE_STUDY | `TEMP_RESIDENCE_STUDY_BASE` | APPLICABILITY (**genuine legal fact**) | The study permit applies to a third-country national currently studying | Lubuskie Voivodeship Office (VERIFIED, same-Tier NATIONAL-rule stand-in) | `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP` = true AND `CURRENTLY_STUDYING` = true | `CITIZENSHIP_COUNTRY` (derived), `CURRENTLY_STUDYING` | AVAILABLE | No | Both PASS | Citizenship is EU/EEA/Swiss | `CURRENTLY_STUDYING` never asked (goal not selected) | n/a — **not published**, see below | **APPROVED, NOT PUBLISHED** (deliberate — see "Why the study rules aren't published") |
| TEMP_RESIDENCE_STUDY | `TEMP_RESIDENCE_STUDY_NOT_STUDY_GOAL` | EXCLUSION (**product policy**) | The user never selected the study goal | Lubuskie Voivodeship Office (VERIFIED) | `PRIMARY_PURPOSE` does not contain `STUDY` | `PRIMARY_PURPOSE` | AVAILABLE | No | Exclusion SATISFIED | Exclusion NOT_SATISFIED | n/a | n/a — **not published** | **APPROVED, NOT PUBLISHED** |

## Why the study rules aren't published

Per the brief's own explicit allowance: "it is acceptable to create a DRAFT/APPROVED
production Rule while Procedure content remains unpublished." `TEMP_RESIDENCE_STUDY`
itself is `READY_FOR_PUBLICATION` (`APPROVED`, not `PUBLISHED`) — correctly held by the
VERIFIED-primary-source publish gate this session's earlier hardening added (see Phase
10's report). Publishing the two Rules while the Procedure stays unpublished would have
been *safe* in the narrow sense — `RecommendationService.buildCandidate` independently
checks for an active `PUBLISHED` `ProcedureVersion` and returns
`UNAVAILABLE_FOR_ANALYSIS` regardless of what the Rules say when none exists — but
publishing a Rule for content that itself isn't cleared for production would blur the
two review gates. The Rules were taken all the way to `APPROVED` (real
`LEGAL_REVIEWER` sign-off) so that publishing them is a one-step action once the
Procedure's own source gap is resolved.

## Fact / Question coverage audit

Every fact a published Rule leaf references, traced to its source:

| Fact code | Question code | Questionnaire version | Visible under which branch | Required? | Production rule(s) | Coverage status |
|---|---|---|---|---|---|---|
| `PRIMARY_PURPOSE` | `PRIMARY_PURPOSE` | v1 & v2 (always visible) | — (top-level, always shown) | Yes | `PESEL_BASE_APPLICABILITY`, `MELDUNEK_BASE_APPLICABILITY`, `TEMP_RESIDENCE_WORK_NOT_WORK_GOAL`, `TEMP_RESIDENCE_STUDY_NOT_STUDY_GOAL` | AVAILABLE |
| `GET_MELDUNEK` (option) | `PRIMARY_PURPOSE` | **v2 only** | — | — | `MELDUNEK_BASE_APPLICABILITY` | **NEEDS_NEW_QUESTION → resolved this phase** (new option, new `QuestionnaireVersion`) |
| `CURRENTLY_IN_POLAND` | `CURRENTLY_IN_POLAND` | v1 & v2 (always visible) | — | Yes | `EU_RESIDENCE_REGISTRATION_BASE` | AVAILABLE |
| `CITIZENSHIP_COUNTRY` | `CITIZENSHIP_COUNTRY` | v1 & v2 (always visible) | — | Yes | (feeds the derived fact below) | AVAILABLE |
| `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP` | — (**derived**, from `CITIZENSHIP_COUNTRY` via `FactResolver`/`CountryClassificationService`) | n/a | n/a | n/a (derived) | `EU_RESIDENCE_REGISTRATION_BASE`, `TEMP_RESIDENCE_WORK_BASE`, `TEMP_RESIDENCE_STUDY_BASE` | **DERIVED** (already implemented, Phase 6) |
| `HAS_JOB_OFFER` | `HAS_JOB_OFFER` | v1 & v2 | Visible only when `PRIMARY_PURPOSE` contains `WORK` or `HIGHLY_QUALIFIED_WORK` | Yes, when visible | `TEMP_RESIDENCE_WORK_BASE` | AVAILABLE (goal-gated by design — this is exactly why `TEMP_RESIDENCE_WORK_NOT_WORK_GOAL` exists) |
| `MONTHLY_GROSS_SALARY` | `MONTHLY_GROSS_SALARY` | v1 & v2 | Visible only when `HAS_JOB_OFFER` = true | No (`allow_unsure` = true) | `TEMP_RESIDENCE_WORK_MIN_WAGE` | AVAILABLE |
| `CURRENTLY_STUDYING` | `CURRENTLY_STUDYING` | v1 & v2 | Visible only when `PRIMARY_PURPOSE` contains `STUDY` | Yes, when visible | `TEMP_RESIDENCE_STUDY_BASE` | AVAILABLE |

**Not used this phase, deliberately**: `MARITAL_STATUS`/`SPOUSE_CITIZENSHIP` (family
reunification — out of scope), `YEARS_IN_POLAND`/`HAS_KARTA_POLAKA` (long-term
residence — out of scope), `EMPLOYMENT_CONTRACT_TYPE`/`HIGHLY_QUALIFIED`/
`STUDY_MODE`/`EXPECTED_GRADUATION_DATE`/`CURRENT_STATUS_EXPIRY_DATE` (collected, but no
production Rule needs them yet — per the data-minimization principle, no Rule was
invented just to make use of an existing question).

**No `NOT_SAFELY_COLLECTIBLE` fact was needed this phase** — every legal distinction
Phase 10's research resolved with confidence (citizenship group, presence, goal,
job-offer, salary, studying) already had a usable Phase 5 fact. The one distinction
Phase 10 flagged as *not* safely collectible yet — the EU registration's >3-month
timing threshold, and Temporary residence for studies' sufficient-funds test — were
correctly left **out** of these Rules rather than approximated (see "Deliberately not
modeled" below).

## QuestionnaireVersion 2 — the one questionnaire change this phase made

- **What**: one new `PRIMARY_PURPOSE` option, `GET_MELDUNEK` ("Register my address
  (meldunek)"), sort order 85 (between `GET_PESEL` at 80 and `UNSURE` at 90). No other
  question, option, or dependency changed — v2 is a byte-for-byte structural clone of
  v1 (18 questions, 18 dependencies, identical options) plus this one addition,
  verified directly against the database before publishing.
- **Why**: `MELDUNEK_BASE_APPLICABILITY` needed a way to represent "the user explicitly
  wants to register their address," mirroring the already-published `GET_PESEL`
  pattern (`RECOMMENDATION_POLICY.md`'s own product-policy-vs-legal-rule distinction,
  and the brief's own explicit "PESEL and Meldunek may reasonably be shown because the
  user explicitly selects them as goals" guidance).
- **How it was authored**: Phase 9's Admin UI deliberately does not expose question/
  option editing (a documented scope cut - see `AdminQuestionnaireController`'s own
  class Javadoc). Consistent with how Question/QuestionOption identity has always been
  seeded in this codebase (V23/V34's "stable identity, not legal content" precedent), a
  new migration (`V47`) mechanically clones v1's structure into a new DRAFT v2 and adds
  the one new option via plain `INSERT ... SELECT` (no hand-transcription of the other
  17 questions, avoiding transcription risk) — verified against the database (18
  questions/18 dependencies in both versions) before proceeding. The version's own
  lifecycle then went through the **real** Admin API exactly like every other content
  version this phase: DRAFT → `CONTENT_EDITOR` submit → `LEGAL_REVIEWER` approve →
  `ADMIN` publish, with a real `AuditLog` entry at each step.
- **A real bug found and fixed in the process**: publishing this real (non-`TEST_*`)
  `QuestionnaireVersion` — the first one in this codebase's history with real
  `submittedBy`/`approvedBy`/`publishedBy` actors — surfaced a `LazyInitializationException`
  in `QuestionnaireVersionRepository.findByQuestionnaire_IdOrderByVersionNumberDesc`,
  the one admin listing query in that repository that hadn't been fetch-joined with its
  actor fields (every other query in the same file already was, per that class's own
  established Phase 9 pattern — this one method was simply missed). Fixed by adding the
  same `LEFT JOIN FETCH` clauses; confirmed by re-listing after the fix and by the full
  backend regression (310/310).
- **Regression discipline**: `v1` was automatically closed (`effectiveTo` set) when
  `v2` published, mirroring `ProcedureVersion`'s own publish-time behavior — any
  assessment already bound to `v1` (there were several, from earlier Playwright runs
  this session) stays on `v1` permanently, per the schema's own foreign key. Confirmed
  directly: v1 shows `effectiveTo = 2026-09-03`, v2 shows `effectiveFrom = 2026-09-03,
  effectiveTo = null`.

## Deliberately not modeled this phase

- **EU registration's >3-month registration-timing threshold** — no duration/arrival-date
  fact exists in Phase 5 yet, and no `DURATION_*` comparison operator exists in
  `ComparisonOperator` at all (`ComparisonOperator.java`'s own Javadoc: "no fact this
  codebase computes is a genuine duration yet ... add them only alongside a real
  duration fact"). `EU_RESIDENCE_REGISTRATION_BASE` correctly treats the procedure as
  *applicable* to any EU/EEA/Swiss citizen currently in Poland — a legally accurate
  statement (they may need to register once they pass 3 months) — without pretending to
  know whether that threshold has already been crossed. The timing detail itself
  remains as text in the already-published procedure content.
- **Temporary residence and work's fee-tier mapping and statutory processing-day
  figure** — both already flagged `OPEN_LEGAL_QUESTIONS.md` items 9-10 from Phase 10;
  neither is an eligibility fact, so neither affects any Rule.
- **Temporary residence for studies' sufficient-funds test** (PLN 823/1,010) — Phase 10
  deliberately left this unencoded as a `Threshold` (currency not independently
  confirmed for 2026); consistently, no Rule in this phase references it either. Its
  `Rule`s (still unpublished) evaluate only citizenship group + study status.
- **Multiple independent legal routes to the same procedure** (e.g. a possible second,
  narrower PESEL route) — out of scope per `RECOMMENDATION_POLICY.md`'s own documented
  boundary (ADR-009): multiple required-type rules on one target are ANDed, not
  OR-alternatives, so a genuinely-alternative legal basis needs real engine work this
  phase does not attempt. `PESEL_BASE_APPLICABILITY` stays a single, honest
  product-relevance rule rather than an inaccurate attempt to model multiple routes.

## Rule roles used

`APPLICABILITY` and `REQUIREMENT` are both "required" for `RecommendationClassifier`
(must be `SATISFIED`, else the candidate is `NOT_APPLICABLE` on a known `FAIL` or
`MORE_INFORMATION_REQUIRED` on `MISSING`); `EXCLUSION` wins outright when `SATISFIED`,
regardless of the required rules' state. No `ELIGIBILITY` or `INFORMATION_REQUIRED`
rule was used this phase — `APPLICABILITY` correctly captures "does this procedure
apply to your situation" for every rule here (none of them assert a full statutory
eligibility determination beyond applicability + the one verified salary requirement),
and no purely-informational condition (one that should produce a reason but never gate
the outcome) was needed.
