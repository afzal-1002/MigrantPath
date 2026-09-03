# Fact Registry

Status: Phase 6 implemented (`FactRegistry`, `com.foreignerwarsaw.rules.evaluation`). This
is the contract between Phase 5 (which collects facts) and Phase 6 (which evaluates
rules over them) — a `RuleVersion` condition leaf may only reference a `fact` code listed
here; `ConditionTreeValidator` rejects anything else at publish time (brief §12/§23).

## Direct facts

Direct facts are exactly Phase 5's `Question.code` rows — this registry never duplicates
that list, it reads `QuestionRepository` live. The table below is the current
`WARSAW_GENERAL_ASSESSMENT` seed (`V38__seed_warsaw_general_assessment.sql`); see
[QUESTION_CODES.md](../product/QUESTION_CODES.md) for why each question exists.

| Code | Type | Cardinality | Allowed operators | Sensitive |
|---|---|---|---|---|
| `CITIZENSHIP_COUNTRY` | COUNTRY | single | `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `EXISTS`, `NOT_EXISTS`, `IS_MEMBER_OF_COUNTRY_GROUP`, `IS_NOT_MEMBER_OF_COUNTRY_GROUP` | nationality |
| `CURRENTLY_IN_POLAND` | BOOLEAN | single | `EQUALS`, `NOT_EQUALS`, `EXISTS`, `NOT_EXISTS` | — |
| `CURRENT_COUNTRY` | COUNTRY | single | (as `CITIZENSHIP_COUNTRY`) | location |
| `DATE_OF_BIRTH` | DATE | single | `EQUALS`, `NOT_EQUALS`, `DATE_BEFORE`, `DATE_BEFORE_OR_EQUAL`, `DATE_AFTER`, `DATE_AFTER_OR_EQUAL`, `EXISTS`, `NOT_EXISTS` | date of birth — prefer the derived `AGE_YEARS` fact in rules |
| `CURRENT_LEGAL_STATUS` | SINGLE_SELECT | single | `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `EXISTS`, `NOT_EXISTS` | immigration status |
| `CURRENT_STATUS_EXPIRY_DATE` | DATE | single | (as `DATE_OF_BIRTH`) | — |
| `PRIMARY_PURPOSE` (a.k.a. `GOALS` in examples) | MULTI_SELECT | set | `CONTAINS`, `NOT_CONTAINS`, `EXISTS`, `NOT_EXISTS` | — |
| `HAS_JOB_OFFER` | BOOLEAN | single | (as `CURRENTLY_IN_POLAND`) | — |
| `EMPLOYMENT_CONTRACT_TYPE` | SINGLE_SELECT | single | (as `CURRENT_LEGAL_STATUS`) | — |
| `MONTHLY_GROSS_SALARY` | DECIMAL (PLN, monthly gross) | single | `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `BETWEEN`, `EXISTS`, `NOT_EXISTS` | income |
| `HIGHLY_QUALIFIED` | BOOLEAN | single | (as `CURRENTLY_IN_POLAND`) | — |
| `CURRENTLY_STUDYING` | BOOLEAN | single | (as `CURRENTLY_IN_POLAND`) | — |
| `STUDY_MODE` | SINGLE_SELECT | single | (as `CURRENT_LEGAL_STATUS`) | — |
| `EXPECTED_GRADUATION_DATE` | DATE | single | (as `DATE_OF_BIRTH`) | — |
| `MARITAL_STATUS` | SINGLE_SELECT | single | (as `CURRENT_LEGAL_STATUS`) | family |
| `SPOUSE_CITIZENSHIP` | COUNTRY | single | (as `CITIZENSHIP_COUNTRY`) | family, nationality |
| `YEARS_IN_POLAND` | INTEGER | single | `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `BETWEEN`, `EXISTS`, `NOT_EXISTS` | — |
| `HAS_KARTA_POLAKA` | BOOLEAN | single | (as `CURRENTLY_IN_POLAND`) | ethnicity-adjacent — Karta Polaka is a legal status, not an ethnicity claim |

Allowed operators are derived mechanically from `QuestionType` (`FactRegistry.operatorsFor`)
— never hand-curated per fact, so a new question type's operator set is defined once.

## Derived facts

Computed deterministically at evaluation time from other direct facts and
`evaluationDate` — never persisted, never a database write, never a legal conclusion
(brief §13's explicit "do not derive `IS_BLUE_CARD_ELIGIBLE` inside a fact resolver" — that
is a whole `RuleVersion`'s job, never a shortcut here).

| Code | Type | Derived from | Computation |
|---|---|---|---|
| `AGE_YEARS` | INTEGER | `DATE_OF_BIRTH` | `Period.between(dateOfBirth, evaluationDate).getYears()` — correct across leap years/birthdays by construction. `null` if `DATE_OF_BIRTH` is absent. |
| `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP` | BOOLEAN | `CITIZENSHIP_COUNTRY` | `CountryClassificationService.isOutsideEuEeaSwissFreeMovementGroup(country, evaluationDate)` (ADR-006) — a structural country-group fact, never a legal "third-country-national" determination on its own. |
| `COUNTRY_GROUP_MEMBERSHIPS` | MULTI_SELECT | `CITIZENSHIP_COUNTRY` | Every group `CountryClassificationService.classificationsFor` returns for the country on `evaluationDate` (memberships legitimately overlap — EEA and SCHENGEN, for example). |

Adding a derived fact means adding a real, tested resolution method to `FactResolver` —
never a speculative entry with no resolution logic behind it (brief §13).

## What is deliberately not a fact

`THIRD_COUNTRY` (a universal boolean) does not exist and must not reappear (ADR-006) — a
procedure's own legal definition of "third-country national" is expressed explicitly in
that `RuleVersion`'s condition tree, from `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP`
plus whatever else that procedure's legal basis actually requires (a Withdrawal
Agreement flag, a statelessness flag, etc.) — never inferred from nationality alone
(brief §26/§27). UK Withdrawal Agreement rights are similarly never inferred from
`CITIZENSHIP_COUNTRY = GB` (brief §27) — no fact for this exists yet; a rule needing it
must return `MISSING`, not guess.
