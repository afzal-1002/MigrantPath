# Recommendation ranking policy

Status: Phase 7 implemented (`RecommendationRanker`,
`com.foreignerwarsaw.recommendation.engine`). Deterministic, two inputs only - no AI, no
percentage, no arbitrary heuristic. Given the same set of classified candidates, this policy
always produces the same order.

## Step 1 - category precedence

Every candidate's category orders first, in this fixed sequence:

```
PRIMARY_MATCH
POSSIBLE_ALTERNATIVE
MORE_INFORMATION_REQUIRED
NOT_APPLICABLE
UNAVAILABLE_FOR_ANALYSIS
```

## Step 2 - PRIMARY_MATCH vs POSSIBLE_ALTERNATIVE

`RecommendationClassifier` never itself outputs `POSSIBLE_ALTERNATIVE` - every match-candidate
starts out `PRIMARY_MATCH`. The ranker then applies exactly one rule, using
`Procedure.recommendationPriority` (nullable, unset for every real Procedure today - see that
field's own Javadoc and [DATABASE.md §6](../database/DATABASE.md#6-recommendation-entities-implemented-phase-7)):

- **If no match-candidate in this run has a `recommendationPriority` set** (the current reality
  for every real procedure), every one of them stays `PRIMARY_MATCH`. Multiple primary matches
  are legitimate (brief §43) - a user can genuinely have several equally-relevant pathways, and
  there is no reviewed signal to rank them against each other.
- **If at least one does**, the single best (lowest) declared priority value among match-
  candidates becomes the "top tier" - only the candidate(s) sharing that value stay
  `PRIMARY_MATCH`; every other match-candidate, including one with no priority set at all, is
  demoted to `POSSIBLE_ALTERNATIVE`.

This is deliberately the only ranking signal that can ever produce `POSSIBLE_ALTERNATIVE` -
brief §24's explicit warning ("do not encode real legal policy without source/review") means this
phase never guesses a priority from procedure metadata, specificity, or anything else. Populating
`recommendationPriority` for a real procedure is a future, deliberate, source-reviewed content
decision - out of Phase 7's own scope.

## Step 3 - tie-break within a category

Within the same category (and, for match-candidates, the same priority tier), candidates are
ordered by the Procedure's stable `code`, alphabetically (brief §44) - never database or
collection retrieval order.

## Final rank assignment

After the two-step sort above, every candidate in the run receives a single, run-wide `rank`
(1-based, strictly increasing) reflecting its final position - the exact number persisted on
`Recommendation.rank` and returned in the API response.

## What this is not

- Not a percentage or confidence score anywhere (brief §23/§52).
- Not influenced by which goals the user selected beyond what already shaped which rules/facts
  were evaluated - a failed procedure never becomes `PRIMARY_MATCH` just because the user
  selected a related goal (brief §25).
- Not aware of `ALTERNATIVE_TO`/`SPECIALIZATION_OF` procedure relationships (brief §47) - deferred,
  same as [RECOMMENDATION_POLICY.md](RECOMMENDATION_POLICY.md)'s alternative-legal-basis gap,
  until a real need and reviewed metadata exist.
