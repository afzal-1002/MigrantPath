-- RuleThresholdReference (docs/database/DATABASE.md §5, brief §21) - the queryable
-- companion to condition_tree's opaque JSONB: "which rules depend on threshold X"
-- becomes a plain indexed query instead of parsing JSON at query time. Populated by
-- RuleVersionService whenever a RuleVersion is saved, by walking the condition tree once
-- and extracting every "threshold" reference (brief §21's own instruction) - kept in
-- sync by always being rebuilt from the tree, never hand-maintained.
--
-- threshold_code (not threshold_id) mirrors DATABASE.md §5's own sketch, and is a real
-- FK against thresholds.code's unique index (brief §70's "no orphan references") - a
-- rule cannot reference a threshold that doesn't exist.
CREATE TABLE rule_threshold_references (
    rule_version_id  UUID NOT NULL REFERENCES rule_versions (id) ON DELETE CASCADE,
    threshold_code    VARCHAR(50) NOT NULL REFERENCES thresholds (code) ON DELETE RESTRICT,
    PRIMARY KEY (rule_version_id, threshold_code)
);

CREATE INDEX rule_threshold_references_threshold_idx ON rule_threshold_references (threshold_code);
