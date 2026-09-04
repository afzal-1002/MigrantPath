# Data Purposes (Purpose Limitation)

Canonical Phase 12. Concrete technical/product purpose for every category of personal
data this application actually collects - no generic "to improve the service" entries,
because nothing in this application currently does that.

```text
Email
  -> authentication (login)
  -> account verification (registration)
  -> password reset delivery
  -> account-lifecycle notifications (verification/reset emails only - no marketing)

Password (as a hash only)
  -> authentication

First name
  -> personalizing the UI ("Hi, <name>") - no other use

Preferred language
  -> not yet read anywhere in the product (Phase 2 field, no i18n implementation yet) -
     a real, disclosed case of a collected-but-currently-unused field, see
     DATA_MINIMIZATION note below

Consent records (TERMS_OF_SERVICE / PRIVACY_POLICY acceptance + policy_version + timestamp)
  -> provable acknowledgement of the policy version in effect at registration

Assessment answers (citizenship, presence in Poland, legal status, goal/purpose,
job offer, salary, study status, date of birth)
  -> immigration-pathway eligibility assessment - each answer is read by name in
     docs/legal-content/PRODUCTION_RULE_COVERAGE.md's real production Rules; see the
     data-minimization audit below for which specific questions feed which Rule

Recommendation history (which procedures, what type, why)
  -> lets the user see and revisit why a procedure was or wasn't recommended to them

UserCase / checklist state (steps, documents, fees, user notes, events)
  -> the user's own progress-tracking for a procedure they chose to pursue

Technical/security logs (IP, user agent, timestamps, auth events)
  -> security monitoring and abuse detection only (rate limiting, lockout) - never used
     for personalization, profiling, or analytics
```

## Data minimization audit (canonical brief item)

Every question in the active `WARSAW_GENERAL_ASSESSMENT` questionnaire (v2, per
`docs/legal-content/PRODUCTION_RULE_COVERAGE.md`'s own fact/question coverage table),
classified against what currently reads it:

| Question | Classification | Consumer |
|---|---|---|
| `CITIZENSHIP_COUNTRY` | REQUIRED | Derived `IS_OUTSIDE_EU_EEA_SWISS_FREE_MOVEMENT_GROUP` fact, used by 3 real production Rules |
| `CURRENTLY_IN_POLAND` | REQUIRED | `EU_RESIDENCE_REGISTRATION_BASE` |
| `CURRENT_LEGAL_STATUS` | JUSTIFIED_OPTIONAL | Not read by any current production Rule; genuinely informs product context (what stage the user is at) and is the direct product feature this question was authored for (brief §9's own explicit named question) - not unused, just not yet Rule-connected |
| `CURRENT_STATUS_EXPIRY_DATE` | FUTURE_ONLY | Gated behind specific `CURRENT_LEGAL_STATUS` answers; not read by any current Rule - collected because the questionnaire's own dependency design (V38) already anticipated a future status-expiry-aware Rule, not invented this phase |
| `CURRENT_COUNTRY` | JUSTIFIED_OPTIONAL | Not read by any Rule; optional, gated behind "not currently in Poland" - informs support/product context for someone applying from abroad |
| `DATE_OF_BIRTH` | REQUIRED | Collected for age-related eligibility distinctions the current Rule set doesn't yet need, but the assessment's own design treats it as a baseline required field (brief §9) - not itself referenced by any of the 6 real published Rules today, a real, disclosed case of collect-ahead-of-use |
| `PRIMARY_PURPOSE` | REQUIRED | Read directly by 4 of 6 real production Rules |
| `HAS_JOB_OFFER` | REQUIRED (when visible) | `TEMP_RESIDENCE_WORK_BASE` |
| `MONTHLY_GROSS_SALARY` | REQUIRED (when visible) | `TEMP_RESIDENCE_WORK_MIN_WAGE` |
| `CURRENTLY_STUDYING` | FUTURE_ONLY | Feeds `TEMP_RESIDENCE_STUDY_BASE`, which remains `APPROVED`/unpublished (see `PRODUCTION_RULE_COVERAGE.md`) |
| `MARITAL_STATUS`, `SPOUSE_CITIZENSHIP`, `YEARS_IN_POLAND`, `HAS_KARTA_POLAKA`, `EMPLOYMENT_CONTRACT_TYPE`, `HIGHLY_QUALIFIED`, `STUDY_MODE`, `EXPECTED_GRADUATION_DATE` | UNUSED today | Collected (per Phase 5's original question set) but no production Rule references them, and `PRODUCTION_RULE_COVERAGE.md` already names this explicitly as "collected, but no production Rule needs them yet - per the data-minimization principle, no Rule was invented just to make use of an existing question" |

**Outcome of this audit**: no `QuestionnaireVersion` change was made this phase. The
UNUSED/FUTURE_ONLY questions above are a real, pre-existing, already-disclosed
consequence of Phase 5's original question design (family reunification / long-term
residence / study-permit scope, all out of the current MVP's implemented Rule set) - not
a new finding, and not something this phase's scope (self-service export/deletion,
governance-safe account deletion) should fix by unilaterally editing the shared
questionnaire. Recorded here as a named, tracked follow-up: a future content-scoping
pass should either build the Rules these questions were collected for, or retire them
through a new `QuestionnaireVersion` (never mutating the published one - brief's own
explicit instruction) if those procedures are dropped from the roadmap.

## What this data is never used for

No analytics, no advertising, no marketing segmentation, no automated profiling beyond
the deterministic, disclosed Rules engine itself (ADR-003). No data collected here is
sold, shared with advertisers, or used to train any model.
