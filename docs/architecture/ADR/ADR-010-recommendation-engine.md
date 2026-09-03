# ADR-010: A separate, immutable, deterministic Recommendation Engine

Status: Accepted — 2026-09-03

## Context

Phase 6 (ADR-009) produces a `RuleEvaluationResult` per rule - a statement about whether *one
rule's* conditions held, never a comparison between rules and never anything a user could act on
directly. Phase 7 had to decide how a user-facing "which pathways are relevant to me" answer gets
built on top of that, without either (a) duplicating eligibility logic outside the rules engine,
or (b) collapsing the deterministic PASS/FAIL/MISSING/ERROR model into a fabricated confidence
number.

## Decision

**A separate `com.foreignerwarsaw.recommendation` module, never a second rules engine.**
`RecommendationClassifier`/`RecommendationRanker`/`RecommendationReasonMapper` consume Phase 6's
`RuleEvaluationBundle` as their only eligibility input - none of them ever reads
`AssessmentFacts` or evaluates a condition itself (brief §14). Phase 6 remains the sole
authority on whether a condition held; Phase 7 only aggregates, classifies, and orders.

**No AI, no confidence percentage, ever** (brief §51/§52/§80) - the same discipline ADR-003
already established for Phase 6, extended to the ranking layer: a `PRIMARY_MATCH` is a
categorical statement ("required rules hold, no exclusion applies, ranked at the top"), never
"93% eligible." Ranking uses exactly two deterministic inputs (category, then an optional,
reviewed-content-only `recommendationPriority`) - see
[RANKING_POLICY.md](../../recommendations/RANKING_POLICY.md).

**Recommendation output is persisted and immutable, not a replace-in-place cache** - this is a
deliberate refinement of the Phase 0 sketch (`DATABASE.md §6`'s original design, which described
`Recommendation` rows as a disposable cache "deleted and replaced" on re-evaluation). The
approved Phase 7 brief requires historical reproducibility: an old analysis must remain viewable
exactly as computed even after rules, procedure content, or thresholds change (brief §37/§61/
§120). A `RecommendationRun` is therefore an append-only fact, like every other `*Version` table
in this codebase, not a query result cache. Re-analysis always creates a new `RecommendationRun`;
nothing is ever updated after a run completes.

**`RecommendationRun.status` can be `PARTIAL`** - one procedure's rule returning `ERROR` (a
content/configuration problem) never invalidates every other procedure's successfully-computed
recommendation, and is never silently reported as `NOT_APPLICABLE` (brief §48/§118, carried
forward from ADR-009's "ERROR is not FAIL" principle at the run level).

**Missing information is a first-class recommendation category** (`MORE_INFORMATION_REQUIRED`),
not an error and not folded into `NOT_APPLICABLE` - the direct consequence of Phase 6 treating
`MISSING` as its own leaf state (ADR-009): a recommendation engine that discarded that
distinction would misrepresent "we don't know yet" as "no."

## Consequences

- Adding a new classification rule means changing `RecommendationClassifier`'s policy table
  (documented, reviewed, tested) - never a database-configurable heuristic.
- Historical reproducibility has a real storage cost (every run's recommendations/reasons persist
  forever) - accepted as the correct trade-off given the brief's explicit requirement; nothing in
  this schema needs bulk deletion/archival machinery yet at Phase 7's expected volume.
- `POSSIBLE_ALTERNATIVE` and alternative-legal-basis rule composition are both real gaps this
  phase left deliberately unfilled rather than guessed at without reviewed content - see
  [RECOMMENDATION_POLICY.md](../../recommendations/RECOMMENDATION_POLICY.md) and
  [RANKING_POLICY.md](../../recommendations/RANKING_POLICY.md) for exactly what is and isn't
  covered, and PHASE_7_REPORT.md for the full list.
- Phase 8 (cases/checklists) consumes a `Recommendation` by reference only - it never mutates
  recommendation history, matching this ADR's immutability principle.

See [RECOMMENDATION_POLICY.md](../../recommendations/RECOMMENDATION_POLICY.md),
[RANKING_POLICY.md](../../recommendations/RANKING_POLICY.md),
[REASON_CODES.md](../../recommendations/REASON_CODES.md), and
[DATABASE.md §6](../../database/DATABASE.md) for the concrete schema/semantics this decision
produced.
