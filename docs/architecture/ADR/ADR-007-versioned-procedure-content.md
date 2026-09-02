# ADR-007: Procedure content is a stable identity plus immutable, source-backed versions

Status: Accepted — 2026-09-02 (Phase 4)

## Context

Phase 4 needs to model administrative procedures (PESEL, Temporary Residence and Work,
...) as reusable, source-backed content the public API can serve, while satisfying two
requirements that pull in different directions: content must be editable and reviewable
(a wrong document requirement needs to be fixed), and a `UserCase` created today (Phase 8)
must be able to look back at exactly what a user was shown, even after that content is
later corrected or the law changes (ADR-004's "never silently mutate an existing user's
case" applies to procedure content just as much as it applies to Rule/Threshold content).

## Decision

**Stable `Procedure` identity, never edited in place, plus append-only
`ProcedureVersion` rows** — the same identity+version split ADR-004 already established
for legal content generally, applied here to procedures/steps/document requirements/fees.

- **Only the active PUBLISHED version is ever returned to public users.** DRAFT,
  IN_REVIEW, and APPROVED versions exist for the content team to work on, but are
  unreachable from `/api/v1/procedures/**` under any query parameter or code path -
  proven by `ProcedureApiIntegrationTest`. This is what makes "in-progress content never
  leaks to the public" a structural guarantee, not a policy someone has to remember.
- **Once PUBLISHED, a version's content is immutable** (`ProcedureVersion.requireMutable`
  throws on any content mutation once the status is `PUBLISHED`/`ARCHIVED`). Changing
  anything - even fixing a typo - means creating a new DRAFT version and running it
  through the same review/publish workflow. This is deliberately inconvenient: it's what
  makes "what did version 3 actually say" a stable, permanent fact.
- **Old versions are never deleted.** A version superseded by a newer one has its
  `effective_to` closed (its content stays queryable for its own historical date range);
  a version withdrawn outside the normal supersession flow is `ARCHIVED` instead -  a
  different state precisely because "this was superseded by something newer" and "this
  was pulled because it was wrong" are different facts worth being able to tell apart
  later (see §110/§111 of this phase's brief).
- **Effective dates exist so future-dated publication is possible without affecting
  current users** - an admin can publish next month's fee change today; nobody sees it
  until the effective date arrives (proven end-to-end by
  `ProcedureVersioningIntegrationTest`'s two-version scenario).
- **Every published version snapshots exactly what a future `UserCase` (Phase 8) will
  need to reference**: the `ProcedureVersion` id itself, plus every `StepVersion`/
  `DocumentRequirementVersion`/`FeeVersion` id created alongside it. Phase 8 pins these
  ids at case-creation time rather than following "whatever the procedure currently
  says" - the same reasoning DATABASE.md §8's `UserCaseRequirementSnapshot` sketch
  already assumed.

## Why never a universal legal boolean here either

Nothing in this content model decides eligibility. `RequirementType.CONDITIONAL` on a
`DocumentRequirementVersion` marks "a future Phase 6 `Rule` will decide whether this
applies to a given user" - it carries no foreign key to a `Rule` table that doesn't exist
yet (brief §16), and Phase 4's own APIs never return a match/eligibility verdict (brief
§90). This mirrors ADR-006's own principle for country classification: a narrow,
honestly-scoped structural fact, never a stand-in for a legal determination a later phase
is responsible for making correctly, per procedure.

## Consequences

- A `Procedure`'s public detail page always reflects "what's true right now," while the
  full history of what changed, when, and on what source basis remains queryable by
  anyone with the DRAFT-visibility (internal) API.
- Publishing is the one moment content becomes both public and permanent for its
  effective range - see
  [CONTENT_PUBLISHING_WORKFLOW.md](../../product/CONTENT_PUBLISHING_WORKFLOW.md) for the
  full state-transition and validation rules, and
  [SOURCE_VERIFICATION_POLICY.md](../../legal-sources/SOURCE_VERIFICATION_POLICY.md) for
  what "VERIFIED" is required to mean before that's allowed to happen.
- Phase 8's `UserCase` snapshot model can be built directly against the version ids this
  phase already produces, with no schema change needed later.
