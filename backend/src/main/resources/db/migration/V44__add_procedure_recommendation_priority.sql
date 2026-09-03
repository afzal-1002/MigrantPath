-- Optional, reviewed-content-only ranking signal (Phase 7 brief §24/§46) - NULL by
-- default and NOT populated by this migration or any other Phase 7 seed (brief §24: "do
-- not encode real legal policy without source/review"). When two Procedures both satisfy
-- their required Rules for the same assessment, RecommendationRanker uses this column,
-- if and only if at least one candidate has it set, to tell PRIMARY_MATCH from
-- POSSIBLE_ALTERNATIVE (docs/recommendations/RANKING_POLICY.md) - lower value ranks
-- higher. Left unset for every real Procedure today; a future content-review pass
-- populates it deliberately, procedure by procedure, never inferred.
ALTER TABLE procedures ADD COLUMN recommendation_priority INTEGER;
