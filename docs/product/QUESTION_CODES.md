# Question Codes — Warsaw General Assessment

Status: Phase 5 MVP seed — 18 questions, seeded by
`backend/src/main/resources/db/migration/V38__seed_warsaw_general_assessment.sql` as
`QuestionnaireVersion` 1 of `WARSAW_GENERAL_ASSESSMENT`. Phase 10.5 (Production Rule
Wiring) added exactly one new `PRIMARY_PURPOSE` option (`GET_MELDUNEK`) as
`QuestionnaireVersion` 2 (`V47__seed_warsaw_general_assessment_v2_meldunek_goal.sql`,
then carried through the real Admin API's DRAFT → review → publish workflow) — no
question codes changed or were added; see the `PRIMARY_PURPOSE` row below and
`docs/legal-content/PRODUCTION_RULE_COVERAGE.md` for why.

`Question.code` is a stable, rule-facing identity (docs/database/DATABASE.md §4, ADR-008)
— a future Phase 6 `RuleCondition` and every `AssessmentAnswer` reference it directly.
**Treat these as API/domain contracts: never rename once in use.** `fieldKey` is the
camelCase name a `RuleCondition.field`/`AssessmentFacts` map key would use.

This is a curated subset of ASSESSMENT_DECISION_TREE.md's full ~80-code brainstorm, not
all of it (brief §42/§87 — "Quality > quantity"). Codes here use this brief's own naming
convention (`CITIZENSHIP_COUNTRY`, not the decision tree's `Q_CITIZENSHIP`); the "Decision
tree source" column cross-references the corresponding `Q_*` node for traceability. A
code with no source is new to this MVP cut (e.g. `HAS_KARTA_POLAKA` maps onto the decision
tree's `Q_KARTA_POLAKA_HOLDER`).

## About you (section `ABOUT_YOU`) — always visible

| Code | Type | Semantic / unit | Decision tree source | Why this fact is needed |
| --- | --- | --- | --- | --- |
| `CITIZENSHIP_COUNTRY` | COUNTRY (reference) | — | `Q_CITIZENSHIP` | Drives `CitizenshipClassification` (EU/EEA/Swiss/third-country) that later phases key every rule set off — never itself a procedure choice. |
| `CURRENTLY_IN_POLAND` | BOOLEAN | — | `Q_IN_POLAND_NOW` | Gates the whole current-status branch and Warsaw-specific municipal procedures. |
| `CURRENT_COUNTRY` | COUNTRY (reference) | — | `Q_CURRENT_LOCATION` | Only relevant if applying from abroad — shown when `CURRENTLY_IN_POLAND = false`. |
| `DATE_OF_BIRTH` | DATE | — | `Q_DATE_OF_BIRTH` | Age-related eligibility conditions in later phases; validated not-in-the-future. |

## Your current status (section `CURRENT_STATUS`) — shown when in Poland

| Code | Type | Decision tree source | Why this fact is needed |
| --- | --- | --- | --- |
| `CURRENT_LEGAL_STATUS` | SINGLE_SELECT | `Q_CURRENT_STATUS` | The single most important gate — routes toward third-country vs. EU-free-movement rule sets (never merged, ASSESSMENT_DECISION_TREE.md Step 2). Options: `NONE, VISA_FREE, SCHENGEN_VISA, POLISH_NATIONAL_VISA, TEMPORARY_RESIDENCE_PERMIT, PERMANENT_RESIDENCE_PERMIT, EU_LONG_TERM_RESIDENT, EU_RESIDENCE_REGISTRATION, FAMILY_MEMBER_EU_CARD, TEMPORARY_PROTECTION, REFUGEE_STATUS, SUBSIDIARY_PROTECTION, PENDING_APPLICATION, OTHER, UNSURE`. |
| `CURRENT_STATUS_EXPIRY_DATE` | DATE (allows "not sure") | `Q_STATUS_VALID_UNTIL` | Shown only for statuses that actually expire; timing-sensitive procedures (renewal windows) in later phases. |

## What do you want to do? (section `YOUR_GOAL`) — always visible

| Code | Type | Decision tree source | Why this fact is needed |
| --- | --- | --- | --- |
| `PRIMARY_PURPOSE` | MULTI_SELECT | `Q_PURPOSE` | Multiple simultaneous goals are normal (brief §75) — activates the Work/Study/Family/Long-term branches additively. `QuestionnaireVersion` 1 options: `WORK, HIGHLY_QUALIFIED_WORK, STUDY, JOIN_SPOUSE, JOIN_FAMILY_OTHER, LONG_TERM_STAY, PERMANENT_SETTLEMENT, GET_PESEL, UNSURE`. **`QuestionnaireVersion` 2 (current, Phase 10.5) adds `GET_MELDUNEK`** ("Register my address (meldunek)", sort order 85, between `GET_PESEL` and `UNSURE`) — the same explicit-intent, product-relevance signal `GET_PESEL` already provides, feeding `MELDUNEK_BASE_APPLICABILITY`. |

## Work (section `WORK`) — shown when `PRIMARY_PURPOSE` contains `WORK` or `HIGHLY_QUALIFIED_WORK`

| Code | Type | Semantic / unit | Decision tree source | Why this fact is needed |
| --- | --- | --- | --- | --- |
| `HAS_JOB_OFFER` | BOOLEAN, required | — | `Q_HAS_JOB_OFFER` | Gates the rest of the work branch — no employer detail is asked before this. |
| `EMPLOYMENT_CONTRACT_TYPE` | SINGLE_SELECT | — | `Q_CONTRACT_TYPE` | Contract type materially changes which work-permit pathway applies. Options: `EMPLOYMENT_CONTRACT, MANDATE_CONTRACT, B2B, OTHER, UNSURE`. |
| `MONTHLY_GROSS_SALARY` | DECIMAL (allows "not sure") | `MONEY` / `PLN_MONTHLY_GROSS` | `Q_SALARY_GROSS_MONTHLY` | Feeds a future `Threshold`-based rule (e.g. Blue Card minimum) — never compared to a literal number in this phase (brief §10). |
| `HIGHLY_QUALIFIED` | BOOLEAN | — | `Q_HIGHLY_QUALIFIED` | Only asked when `HIGHLY_QUALIFIED_WORK` was selected as a goal. |

## Study (section `STUDY`) — shown when `PRIMARY_PURPOSE` contains `STUDY`

| Code | Type | Decision tree source | Why this fact is needed |
| --- | --- | --- | --- |
| `CURRENTLY_STUDYING` | BOOLEAN, required | `Q_CURRENTLY_ENROLLED` | Gates the rest of the study branch. |
| `STUDY_MODE` | SINGLE_SELECT | `Q_STUDY_MODE` | Full-time study is what temporary-residence-for-studies pathways require. Options: `FULL_TIME, PART_TIME`. |
| `EXPECTED_GRADUATION_DATE` | DATE (allows "not sure") | `Q_EXPECTED_GRADUATION_DATE` | Graduate-stay pathway timing in later phases. |

## Family (section `FAMILY`) — shown when `PRIMARY_PURPOSE` contains `JOIN_SPOUSE` or `JOIN_FAMILY_OTHER`

| Code | Type | Decision tree source | Why this fact is needed |
| --- | --- | --- | --- |
| `MARITAL_STATUS` | SINGLE_SELECT, required | `Q_FAMILY_RELATION` | Family-reunification pathways branch on this. Options: `SINGLE, MARRIED, OTHER`. |
| `SPOUSE_CITIZENSHIP` | COUNTRY (reference) | `Q_SPOUSE_NATIONALITY` | A Polish/EU-citizen sponsor routes to an entirely different rule set than a third-country one (brief §12/§17) — shown only when `MARITAL_STATUS = MARRIED`. |

## Time in Poland (section `LONG_TERM`) — shown when `PRIMARY_PURPOSE` contains `LONG_TERM_STAY` or `PERMANENT_SETTLEMENT`

| Code | Type | Decision tree source | Why this fact is needed |
| --- | --- | --- | --- |
| `YEARS_IN_POLAND` | INTEGER, required | `Q_YEARS_IN_POLAND` | Permanent residence / EU long-term resident pathways have duration thresholds. |
| `HAS_KARTA_POLAKA` | BOOLEAN | `Q_KARTA_POLAKA_HOLDER` | A distinct, faster pathway in Polish immigration law. |

## Deliberately excluded from this MVP cut

`CURRENT_CITY`, absence/continuity-of-residence detail, permit history, and most of the
decision tree's Step 5 fields are not yet asked — no rule consuming them exists yet, and
asking anyway would violate data minimization (brief §22). Add them as their own
`Question` rows, with their own justification row in this document, only once a Phase 6
rule actually needs them.
