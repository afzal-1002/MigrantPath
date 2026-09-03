-- Recommendation / RecommendationReason (Phase 7) - one row per candidate Procedure
-- within one RecommendationRun, immutable once its run completes (V42's Javadoc-style
-- comment explains why this differs from the Phase 0 DATABASE.md §6 sketch).
--
-- procedure_version_id/rule_version_id references make a stored recommendation
-- self-describing for reproducibility (brief §60/§61) without needing a JSONB snapshot
-- (brief §67: "do not dump the entire result into one opaque column unless there is a
-- strong reason" - there isn't one here, the data is small and genuinely relational).
CREATE TABLE recommendations (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_run_id     UUID NOT NULL REFERENCES recommendation_runs (id) ON DELETE CASCADE,
    procedure_id              UUID NOT NULL REFERENCES procedures (id) ON DELETE RESTRICT,
    -- Nullable: an UNAVAILABLE_FOR_ANALYSIS recommendation (brief §48 - a Rule ERROR, or
    -- no active PUBLISHED ProcedureVersion at evaluation_date, brief §28) may have no
    -- resolvable version to point at.
    procedure_version_id      UUID REFERENCES procedure_versions (id) ON DELETE RESTRICT,
    recommendation_type       VARCHAR(30) NOT NULL,
    -- Deterministic display order within the run (brief §22/§44) - never database
    -- retrieval order. 1-based; ties broken by RecommendationRanker before insert, so
    -- this column always already reflects the final order.
    rank                      INT NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (recommendation_type IN (
        'PRIMARY_MATCH', 'POSSIBLE_ALTERNATIVE', 'MORE_INFORMATION_REQUIRED',
        'NOT_APPLICABLE', 'UNAVAILABLE_FOR_ANALYSIS'
    )),
    CHECK (rank > 0)
);

CREATE UNIQUE INDEX recommendations_run_procedure_uq ON recommendations (recommendation_run_id, procedure_id);
CREATE INDEX recommendations_run_rank_idx ON recommendations (recommendation_run_id, rank);
CREATE INDEX recommendations_run_type_idx ON recommendations (recommendation_run_id, recommendation_type);
CREATE INDEX recommendations_procedure_idx ON recommendations (procedure_id);

-- Structured, machine-readable "why" (brief §10-§12) - stable codes, never persisted
-- English prose. condition_code/fact_code let a reason point back at exactly which leaf
-- of which RuleVersion produced it, without ever exposing the raw JSON path as UX.
CREATE TABLE recommendation_reasons (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id UUID NOT NULL REFERENCES recommendations (id) ON DELETE CASCADE,
    reason_type       VARCHAR(30) NOT NULL,
    reason_code       VARCHAR(100) NOT NULL,
    rule_version_id   UUID REFERENCES rule_versions (id) ON DELETE SET NULL,
    condition_code    VARCHAR(100),
    fact_code         VARCHAR(100),
    message_key       VARCHAR(200),
    display_order     INT NOT NULL DEFAULT 0,
    CHECK (reason_type IN (
        'MATCHED_CONDITION', 'FAILED_CONDITION', 'MISSING_INFORMATION', 'EXCLUSION',
        'ALTERNATIVE_PATH', 'PROCEDURE_PRIORITY', 'ANALYSIS_ERROR'
    ))
);

CREATE INDEX recommendation_reasons_recommendation_idx ON recommendation_reasons (recommendation_id, display_order);
