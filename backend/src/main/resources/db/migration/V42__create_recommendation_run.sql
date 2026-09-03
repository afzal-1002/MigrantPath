-- RecommendationRun (Phase 7, ADR-010, docs/recommendations/) - one immutable record of
-- "what did the engine conclude for this assessment, evaluated on this date, using
-- these engine versions." Deliberately NOT the Phase 0 DATABASE.md §6 sketch's
-- replace-in-place "Recommendation" cache: this phase's brief explicitly requires
-- historical reproducibility (an old run must remain viewable byte-for-byte even after
-- rules/procedures/thresholds change) - see PHASE_7_REPORT.md "Deviations" for the
-- reasoning. A new analysis always creates a new run; nothing here is ever updated after
-- completion, only inserted once.
CREATE TABLE recommendation_runs (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assessment_id                   UUID NOT NULL REFERENCES assessments (id) ON DELETE CASCADE,
    -- The one evaluationDate every downstream Rule/Threshold/ProcedureVersion/country-
    -- group resolution in this run used (brief §58) - never re-derived independently by
    -- a sub-step.
    evaluation_date                 DATE NOT NULL,
    status                          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    -- Two independent version stamps (brief §21/§54): this run's own classification/
    -- ranking semantics, and the Phase 6 engine semantics it built on - a later change
    -- to either is distinguishable from a genuine content change when replaying history.
    recommendation_engine_version   VARCHAR(20) NOT NULL,
    rule_engine_version              VARCHAR(20) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at                    TIMESTAMPTZ,
    CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED'))
);

CREATE INDEX recommendation_runs_user_created_idx ON recommendation_runs (user_id, created_at DESC);
CREATE INDEX recommendation_runs_assessment_created_idx ON recommendation_runs (assessment_id, created_at DESC);
