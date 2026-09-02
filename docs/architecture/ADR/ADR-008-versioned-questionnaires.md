# ADR-008: The questionnaire is a versioned identity, and Phase 5 is authenticated-only

Status: Accepted — 2026-09-02 (Phase 5)

## Context

DATABASE.md §4 originally sketched a simpler questionnaire model: a single
`Questionnaire`/`Question` pair with no version lifecycle ("Deliberately no
`QuestionnaireVersion` lifecycle table" - an `AssessmentAnswer` was reasoned to be a
complete record of what was asked regardless of whether `Question` definitions stayed
frozen) and a nullable `Assessment.user_id` to allow starting the wizard before
registering, claimed on account creation. Neither had been built yet when Phase 5's brief
arrived with an explicit, more detailed specification calling for the opposite on both
points: a full `Questionnaire`/`QuestionnaireVersion` identity+version split mirroring
Procedure's (ADR-007), and an authenticated-only flow with no anonymous/guest mode.
Since nothing had been implemented against the original sketch yet, this is a genuine
design decision to make now, not a migration of existing data or behavior.

## Decision

**1. `Questionnaire`/`QuestionnaireVersion` reuses the Procedure identity+version
pattern exactly**, including reusing `PublicationStatus`/`PublicationStateMachine`
directly from the `procedure` package rather than duplicating the lifecycle.

- An `Assessment` binds permanently to one `QuestionnaireVersion.id` at creation time
  and never re-resolves to a newer version while `IN_PROGRESS` — publishing
  `QuestionnaireVersion` 2 must never retroactively change what an assessment already
  bound to version 1 sees, proven by `QuestionnaireVersionImmutabilityIntegrationTest`.
  This matters more here than the original sketch assumed: once branching *structure*
  (which questions exist, how they're gated) can change between versions, not just
  wording, an in-progress assessment's visibility computation would otherwise become
  unstable mid-flow.
- The same "closing the previous version's `effective_to` must be flushed before the new
  version's own publish update, or the exclusion constraint rejects it against the stale
  still-open range" ordering bug ADR-007's `ProcedurePublishingService` already found and
  fixed reappeared verbatim in `QuestionnaireVersionService.publish` and got the same
  fix (`saveAndFlush`, not `save`) — direct evidence the shared pattern is paying for
  itself.

**2. Phase 5 is authenticated-only.** `Assessment.user` is `NOT NULL`; there is no
`anonymous_session_token` and no claiming flow. Starting/resuming/answering/completing an
assessment all require a session, matching `SecurityConfig`'s existing
`anyRequest().authenticated()` default with no new public routes added under
`/api/v1/assessments/**` or `/api/v1/questionnaires/**`.

- Anonymous-then-claimed assessments remain a reasonable *future* product idea (lower
  friction for a first-time visitor who hasn't decided to register yet), but building the
  claiming transition (linking a token-identified assessment to a `user_id` on
  registration/login, deciding what happens if that account already has one) is real,
  separate work with its own edge cases and its own tests — cutting it now keeps this
  phase's scope to what it can actually finish and verify end to end.

**3. `AssessmentAnswer` uses typed nullable columns
(`string_value`/`boolean_value`/`integer_value`/`decimal_value`/`date_value`/
`reference_code`) plus a join table for multi-select, not a single JSONB `value`
column.** The original sketch's JSONB choice was reasonable on its own terms (one column
handles every shape uniformly); the brief's explicit example of what "good" looks like
here — never storing `MONTHLY_GROSS_SALARY` as a string a rule engine has to parse — reads
as the more defensible choice specifically because Phase 6 will read these values
directly by stable `Question.code` for numeric/date comparison, where a native SQL
column type is a stronger guarantee than "the JSON stored happens to be a number."

## Why question branching still isn't the Rules Engine

`QuestionDependency` answers exactly one question — "should this question currently be
shown" — using the same `ComparisonOperator` vocabulary
(`com.foreignerwarsaw.common.evaluation.ConditionEvaluator`) Phase 6's `RuleCondition`
will reuse, per IMPLEMENTATION_PLAN.md 5.2's explicit instruction not to build two
incompatible evaluators. It never produces a match/eligibility verdict, never touches
`Threshold`, and nothing in the Angular wizard predicts branching client-side — every
Next/Back round-trips to the backend, which is the sole authority on what's currently
visible/required (mirrors ADR-007's "narrow, honestly-scoped structural fact, never a
stand-in for a legal determination" for country classification and procedure content
alike).

## Consequences

- DATABASE.md §4, IMPLEMENTATION_PLAN.md's Phase 5 section, and
  `docs/product/QUESTION_CODES.md` are updated to match this decision rather than the
  earlier sketch.
- A future "anonymous assessment" phase adds `anonymous_session_token` and a claiming
  service without touching the version-binding or typed-answer decisions above — neither
  is coupled to the authentication model.
- Phase 6's rule engine can read `AssessmentAnswer` scalar columns directly by
  `Question.code`, and can share `ConditionEvaluator` with `QuestionVisibilityService`
  rather than reimplementing comparison semantics.
