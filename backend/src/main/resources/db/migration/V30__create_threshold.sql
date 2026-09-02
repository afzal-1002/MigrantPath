-- Threshold (identity) / ThresholdVersion (docs/database/DATABASE.md §3,
-- IMPLEMENTATION_PLAN.md 4.6). Unlike Fee, a Threshold is NOT owned by any one
-- procedure - it's a standalone, independently-versioned numeric fact (e.g. a Blue Card
-- salary minimum) a future Phase 6 RuleCondition references by code. Same
-- identity+version+exclusion-constraint pattern as ProcedureVersion, for the same
-- "only one PUBLISHED value may apply on any given date" reason.
--
-- No rows are seeded here (brief §21/§53: never seed unverified legal numeric
-- thresholds just to populate the table) - this migration only builds the engine.
CREATE TABLE thresholds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL,
    -- DECIMAL/INTEGER/MONEY populate `value`; TEXT populates `value_text` instead - two
    -- nullable columns, not a fully generic EAV design (brief §21: "avoid
    -- overengineering polymorphic values").
    value_type      VARCHAR(20) NOT NULL,
    unit            VARCHAR(50),
    -- VARCHAR, not CHAR - same Java-String-maps-to-VARCHAR reasoning as fee_versions
    -- (V29) and Phase 3's V7.
    currency        VARCHAR(3),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (value_type IN ('DECIMAL', 'INTEGER', 'PERCENTAGE', 'DURATION', 'MONEY', 'TEXT')),
    CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX thresholds_code_uq ON thresholds (code);

CREATE TABLE threshold_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    threshold_id    UUID NOT NULL REFERENCES thresholds (id) ON DELETE RESTRICT,
    value           NUMERIC(18, 4),
    value_text      TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_from  DATE,
    effective_to    DATE,
    notes           TEXT,
    created_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    approved_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    published_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    submitted_at    TIMESTAMPTZ,
    approved_at     TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_version    BIGINT NOT NULL DEFAULT 0,
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from),
    CHECK (value IS NOT NULL OR value_text IS NOT NULL)
);

CREATE INDEX threshold_versions_threshold_status_idx ON threshold_versions (threshold_id, status);
CREATE INDEX threshold_versions_effective_idx ON threshold_versions (effective_from, effective_to);

ALTER TABLE threshold_versions
    ADD CONSTRAINT threshold_versions_no_overlapping_published
    EXCLUDE USING gist (
        threshold_id WITH =,
        daterange(effective_from, effective_to) WITH &&
    ) WHERE (status = 'PUBLISHED');
