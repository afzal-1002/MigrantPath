# Assessment Decision Tree — Foreigner Warsaw

Status: DRAFT (Phase 0) — reviewed against the required flow below
Last updated: 2026-09-01

## Phase 0 review note

Confirmed on review: the wizard order is Citizenship (Step 1) → current
location/status (Step 1) → current legal stay (Step 2) → goal/purpose (Step 3) →
purpose-specific questions — work / study / family (Step 4) → other relevant
circumstances — history in Poland (Step 5) → Rules Engine (Step 7) → possible
procedures. It never opens with "which permit do you want" (Product Requirements §5),
and "Browse procedures" (bottom of this document) remains a fully separate path for
users who already know what they need.

**Country and legal classification are separate fields, never conflated.** Step 1
asks `Q_CITIZENSHIP` (a raw ISO 3166 country); `CitizenshipClassification` is a value
*derived* from it via the `CountryGroup` reference data. Concretely:

```
Correct:    Q_CITIZENSHIP = "PK"  →  derive  →  THIRD_COUNTRY_NATIONAL  →  (feeds rules)
Wrong:      "PK" → hard-coded "recommend Temporary Residence and Work"
```

A Pakistani citizenship value never by itself selects a procedure — it only ever
resolves to a classification, which then participates in rule evaluation alongside
purpose, employment, family, and history answers. Genuine per-country exceptions (e.g.
a document-legalisation or driving-licence-recognition rule that really does vary by
issuing country) still enter the model as data keyed on `Country`/`CountryGroup`, never
as a per-country procedure choice — see
[ARCHITECTURE.md §7](../architecture/ARCHITECTURE.md#7-rules-engine).

This document defines the initial "Help me choose" questionnaire: what is asked, in what
order, which answers gate which follow-up questions, and what each branch feeds into the
Rules Engine (see [ARCHITECTURE.md](../architecture/ARCHITECTURE.md)). It implements the
question groups from Product Requirements §6.3, using the field lists already scoped for
Personal / Citizenship / Current status / Purpose / Employment / Student / Family /
Long-term residence.

Every question node lists: `code` (maps to `Question.code`), the field it sets, and its
`dependsOn` gate (skip logic). Terminal nodes marked **→ Recommendation Engine** hand off
to rule evaluation, not another question.

## Wizard steps (UI grouping — Product Requirements §6.3 / brief §45)

1. About you
2. Your current status in Poland
3. What do you want to do? (purpose, multi-select)
4. Purpose-specific questions (work / study / family — only the branches matching step 3)
5. Your history in Poland (duration/continuity, only if long-term-relevant)
6. Review answers
7. See pathways → Recommendation Engine

---

## Step 1 — About you

```
Q_CITIZENSHIP (citizenship, ISO 3166 country picker, multi-select for dual citizenship)
  ↓
Q_CURRENT_LOCATION (current country/city)
  ↓
Q_IN_POLAND_NOW (boolean: currently in Poland?)
  ↓
Q_DATE_OF_BIRTH (date) → derives ageYears, isMinor
```

The system derives **CitizenshipClassification** from `Q_CITIZENSHIP` against the
`CountryGroup` reference table — this is never asked directly of the user (Product
Requirements / brief §7):

```
citizenship ∈ {PL}                          → POLISH_CITIZEN
citizenship ∈ EU_MEMBER_STATES               → EU_CITIZEN
citizenship ∈ {IS, LI, NO}                   → EEA_CITIZEN
citizenship ∈ {CH}                           → SWISS_CITIZEN
citizenship == null / stateless flag         → STATELESS
citizenship ∈ {GB} AND withdrawal-agreement flag (Q_UK_WA_CASE) → UK_WA_CASE
otherwise                                    → THIRD_COUNTRY_NATIONAL
```

`FAMILY_MEMBER_OF_EU_CITIZEN` is a second, independent classification derived from Step
4's family branch (a third-country national married to an EU citizen is still
`THIRD_COUNTRY_NATIONAL` *and* `FAMILY_MEMBER_OF_EU_CITIZEN`) — these are not mutually
exclusive, so classification is modeled as a set, not a single enum value.

---

## Step 2 — Current legal status

```
Q_CURRENT_STATUS (single-select, dependsOn: Q_IN_POLAND_NOW == true)
  Options:
    NONE
    VISA_FREE_STAY
    SCHENGEN_VISA
    NATIONAL_VISA
    TEMP_RESIDENCE_PERMIT
    PERMANENT_RESIDENCE_PERMIT
    EU_LONG_TERM_RESIDENT_PERMIT
    EU_CITIZEN_REGISTRATION
    EU_FAMILY_MEMBER_CARD
    TEMPORARY_PROTECTION
    REFUGEE_STATUS
    SUBSIDIARY_PROTECTION
    HUMANITARIAN_STAY
    TOLERATED_STAY
    PENDING_APPLICATION
    OTHER
    UNSURE
  ↓ (if TEMP_RESIDENCE_PERMIT / PERMANENT_RESIDENCE_PERMIT / EU_LONG_TERM_RESIDENT_PERMIT
     / EU_CITIZEN_REGISTRATION / EU_FAMILY_MEMBER_CARD / TEMPORARY_PROTECTION /
     REFUGEE_STATUS / SUBSIDIARY_PROTECTION / HUMANITARIAN_STAY / TOLERATED_STAY)
  Q_STATUS_ISSUING_COUNTRY, Q_STATUS_VALID_FROM, Q_STATUS_VALID_UNTIL
  ↓ (if PENDING_APPLICATION)
  Q_PENDING_PROCEDURE_TYPE (free text / known-procedure picker)
  ↓ (if UNSURE)
  → branch to a short clarifying sub-questionnaire (has document? photo/OCR out of
    scope V1 — user picks from a plain-language list of common documents: "a stamp in
    my passport", "a plastic card", "a letter saying my application was received", etc.)
    rather than forcing a guess.
```

`Q_CURRENT_STATUS` is the single most important gate: `EU_CITIZEN` /
`EEA_CITIZEN` / `SWISS_CITIZEN` classifications route toward Section B (EU free
movement) of the Procedure Catalogue; everyone else routes toward Section A
(third-country) — these paths do not share rule sets even where the questions look
similar (brief §17, "must be separate from third-country immigration rules").

---

## Step 3 — Purpose (multi-select, brief §9)

```
Q_PURPOSE (multi-select)
  WORK
  HIGHLY_QUALIFIED_WORK
  STUDY
  CONTINUE_UNIVERSITY
  GRADUATE_JOB_SEARCH
  START_BUSINESS
  CONDUCT_BUSINESS
  JOIN_SPOUSE
  JOIN_PARENT
  JOIN_CHILD
  JOIN_FAMILY_OTHER
  RESEARCH
  INTERNSHIP_TRAINEESHIP
  VOLUNTEERING
  POSTED_WORK
  INTRA_COMPANY_TRANSFER
  SEASONAL_WORK
  LONG_TERM_STAY
  PERMANENT_SETTLEMENT
  GET_PESEL
  REGISTER_ADDRESS
  EXCHANGE_DRIVING_LICENCE
  OTHER
```

Each selected value activates a question branch in Step 4. Branch activation is
additive: a user selecting both `WORK` and `JOIN_SPOUSE` sees both the Employment
branch and the Family branch, and the Recommendation Engine may return multiple
`PRIMARY_MATCH`/`POSSIBLE_ALTERNATIVE` results (one per applicable procedure family).

`GET_PESEL`, `REGISTER_ADDRESS`, `EXCHANGE_DRIVING_LICENCE` skip Step 4 entirely (no
employment/study/family data needed) and route straight to the Administrative Services
/ Driving procedures (Catalogue §E/§F) with only Steps 1–2 answers as input.

---

## Step 4a — Employment branch (dependsOn: Q_PURPOSE ∋ {WORK, HIGHLY_QUALIFIED_WORK,
POSTED_WORK, INTRA_COMPANY_TRANSFER, SEASONAL_WORK})

```
Q_EMPLOYED (boolean)
  ↓ true
  Q_EMPLOYER_IN_POLAND (boolean)
  Q_HAS_JOB_OFFER (boolean)
  Q_CONTRACT_TYPE (enum: EMPLOYMENT_CONTRACT, MANDATE_CONTRACT, B2B, OTHER, UNSURE)
  Q_SALARY_GROSS_MONTHLY (number, PLN) — skipped if Q_HAS_JOB_OFFER == false
  Q_OCCUPATION (free text / occupation picker)
  Q_HIGHLY_QUALIFIED (boolean) — only asked if Q_PURPOSE ∋ HIGHLY_QUALIFIED_WORK,
      else derived "unknown" and left for Recommendation Engine to flag as
      MORE_INFORMATION_REQUIRED
  Q_HAS_DEGREE (boolean)
  Q_REGULATED_PROFESSION (boolean)
  Q_WORK_AUTHORIZATION_EXISTS (boolean) — "do you already have permission to work?"
  Q_EMPLOYER_CHANGE / Q_POSITION_CHANGE / Q_SALARY_CHANGE
      (dependsOn: Q_CURRENT_STATUS == TEMP_RESIDENCE_PERMIT — i.e. only relevant to
       people already holding a work-based permit who may need to notify a change)
  Q_SEASONAL_WORK (boolean) — dependsOn: Q_PURPOSE ∋ SEASONAL_WORK
  Q_POSTED_BY_FOREIGN_EMPLOYER (boolean) — dependsOn: Q_PURPOSE ∋ POSTED_WORK
  Q_ICT_TRANSFER (boolean) — dependsOn: Q_PURPOSE ∋ INTRA_COMPANY_TRANSFER
  → Recommendation Engine
      (candidates: Temporary Residence and Work, EU Blue Card, Posted Worker,
       Intra-Company Transfer, Seasonal Work — per Catalogue §A)
```

No question here ever compares `Q_SALARY_GROSS_MONTHLY` to a literal number in the
question definition; the comparison happens inside a `RuleCondition` referencing a
`Threshold` (e.g. `BLUE_CARD_MIN_SALARY`), per Product Requirements §6.3 / brief §10.

## Step 4b — Student branch (dependsOn: Q_PURPOSE ∋ {STUDY, CONTINUE_UNIVERSITY,
GRADUATE_JOB_SEARCH})

```
Q_UNIVERSITY (text / institution picker)
Q_STUDY_MODE (FULL_TIME, PART_TIME)
Q_DEGREE_TYPE (BACHELOR, MASTER, PHD, PREPARATORY_COURSE, OTHER)
Q_CURRENTLY_ENROLLED (boolean)
Q_STUDIES_STARTED (boolean)
Q_TUITION_PAID (boolean)
Q_SCHOLARSHIP (boolean)
Q_EXPECTED_GRADUATION_DATE (date)
Q_GRADUATING_SOON (boolean) — derivable from Q_EXPECTED_GRADUATION_DATE but asked
    directly too, since users may know this before a firm date is set
Q_PREVIOUS_PL_GRADUATE (boolean)
Q_WANTS_TO_WORK (boolean)
Q_WANTS_TO_STAY_AFTER_GRADUATION (boolean)
  ↓ (if Q_STUDY_MODE == FULL_TIME and Q_CURRENTLY_ENROLLED == true)
  → Recommendation Engine candidate: Temporary Residence for Studies
  ↓ (if Q_GRADUATING_SOON == true or Q_WANTS_TO_STAY_AFTER_GRADUATION == true)
  → Recommendation Engine candidate: Graduate-related stay (flagged
    MORE_INFORMATION_REQUIRED until that procedure is researched — see Catalogue,
    currently NOT_STARTED)
```

## Step 4c — Family branch (dependsOn: Q_PURPOSE ∋ {JOIN_SPOUSE, JOIN_PARENT,
JOIN_CHILD, JOIN_FAMILY_OTHER})

```
Q_FAMILY_RELATION (SPOUSE, CHILD, PARENT, OTHER_DEPENDENT)
  ↓ SPOUSE
  Q_SPOUSE_NATIONALITY (ISO 3166) → classification via same CountryGroup lookup as Step 1
  Q_SPOUSE_STATUS_IN_POLAND (Polish citizen / EU citizen / third-country w/ own permit)
  Q_MARRIAGE_DATE (date)
  Q_MARRIAGE_REGISTERED_IN_POLAND (boolean)
  Q_SPOUSE_CURRENTLY_IN_POLAND (boolean)
  ↓ CHILD / PARENT / OTHER_DEPENDENT
  Q_FAMILY_MEMBER_NATIONALITY
  Q_FAMILY_MEMBER_STATUS
  Q_CHILD_AGE (if CHILD)
  Q_DEPENDENCY (boolean — financial/legal dependency)
  Q_SPONSOR_PERMIT_TYPE
  Q_SPONSOR_RESIDENCE_DURATION
  Q_SPONSOR_PROTECTION_STATUS (dependsOn: sponsor holds refugee/subsidiary protection)
  → Recommendation Engine
      (candidate selection branches strictly on Q_SPOUSE_STATUS_IN_POLAND /
       Q_FAMILY_MEMBER_STATUS: a Polish-citizen sponsor routes to "family reunification —
       spouse of Polish citizen"; an EU-citizen sponsor routes to the EU free-movement
       family-member track (Catalogue §B), never the same rule set — brief §12/§17)
```

## Step 5 — History in Poland (dependsOn: Q_PURPOSE ∋ {LONG_TERM_STAY,
PERMANENT_SETTLEMENT} OR Q_CURRENT_STATUS ∈ {TEMP_RESIDENCE_PERMIT, ...} for ≥3 years
cumulative per user profile)

```
Q_YEARS_IN_POLAND (number)
Q_CONTINUITY_OF_RESIDENCE (boolean / describe absences)
Q_PERMIT_HISTORY (multi-entry: permit type + date range)
Q_MARRIED_TO_POLISH_CITIZEN_DURATION (dependsOn: family branch SPOUSE + spouse is
    Polish citizen)
Q_POLISH_ORIGIN (boolean)
Q_KARTA_POLAKA_HOLDER (boolean)
Q_CHILD_OF_POLISH_CITIZEN / Q_CHILD_OF_PERMANENT_RESIDENT (boolean)
Q_STABLE_INCOME (boolean, dependsOn: relevant pathway requires it)
  → Recommendation Engine candidates: Permanent Residence Permit, EU Long-Term Resident
    Permit, Karta Polaka pathway (all currently NOT_STARTED in Catalogue — surfaced as
    MORE_INFORMATION_REQUIRED / informational only until researched and implemented)
```

---

## Step 6 — Review answers

Read-only summary grouped by wizard step, with an "edit" affordance back into any step
(re-entering a step re-evaluates downstream dependsOn gates and may hide/show later
questions — this must not silently discard already-collected answers that remain valid).

## Step 7 — See pathways → Recommendation Engine

Hands the full `AssessmentAnswer` set to the Rules Engine
(see [ARCHITECTURE.md](../architecture/ARCHITECTURE.md#rules-engine)). Output rendering
follows Product Requirements §6.3 / brief §27/§33: ranked recommendations with matched
conditions, missing information, explanation, and sources — never a bare percentage.

---

## "Browse procedures" (Option B) — no questionnaire

Independent of the wizard above, the category tree from the Procedure Catalogue is
directly navigable:

```
Residence
  → Temporary Residence
      → Work
      → Highly Qualified Employment / EU Blue Card
      → Studies
      → Family Reunification
  → Permanent / Long-Term Residence
EU Free Movement
  → EU Citizen Registration
  → Family Member Residence Card
Driving
  → Exchange Foreign Driving Licence
Administrative Services
  → PESEL
  → Address Registration (Meldunek)
Business
Long-Term Stay
```

Selecting a leaf here goes straight to the procedure detail page (eligibility summary,
steps, documents, fees, sources) and can create a `UserCase` without ever running the
questionnaire.

## Notes for implementation

- Every `Q_*` code above becomes a `Question.code`; every `dependsOn` becomes a
  `QuestionDependency` row, not an `if` statement in Angular or Java (brief §45's
  branching requirement + Architecture doc's "legal content vs application code"
  separation).
- "I don't know / not sure" must be a valid answer value on every question feeding a
  legally significant condition (brief §77) — modeled as a sentinel answer value, not a
  null, so the Rules Engine can distinguish "answered unsure" from "not yet asked."
- This tree covers the MVP procedure set's inputs. Purpose values whose procedures are
  `NOT_STARTED` in the Catalogue (e.g. `START_BUSINESS`, `RESEARCH`) are included here so
  the questionnaire schema doesn't need reshaping later, but their Recommendation Engine
  candidates return `MORE_INFORMATION_REQUIRED` with an explanation that the pathway is
  not yet available, rather than a fabricated recommendation.
