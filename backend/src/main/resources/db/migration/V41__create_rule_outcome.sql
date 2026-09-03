-- RuleOutcome (docs/database/DATABASE.md §5, brief §36) - a forward-looking extension
-- point for rule composition/reuse (e.g. a reusable sub-rule other rules could
-- reference by outcome code), deliberately NOT wired into evaluation logic yet (brief
-- §24: "avoid allowing one rule to depend on another RuleVersion unless clearly
-- needed" - Phase 6's condition trees are standalone). Schema placeholder only, so a
-- later phase that does need this doesn't require a migration to add it - see
-- RuleOutcome.java's Javadoc for the same note.
CREATE TABLE rule_outcomes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_version_id   UUID NOT NULL REFERENCES rule_versions (id) ON DELETE CASCADE,
    outcome_code      VARCHAR(50) NOT NULL,
    description       VARCHAR(500),
    UNIQUE (rule_version_id, outcome_code)
);
