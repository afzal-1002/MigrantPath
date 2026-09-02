-- Stable Procedure identity (docs/database/DATABASE.md §3) - "What is this procedure?"
-- only (Phase 4 brief's own core distinction). Never encodes eligibility conditions;
-- that's Phase 6's Rule/RuleVersion, referenced from nowhere in this table.
CREATE TABLE procedures (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 VARCHAR(50) NOT NULL,
    category_id          UUID NOT NULL REFERENCES procedure_categories (id) ON DELETE RESTRICT,
    canonical_name       VARCHAR(300) NOT NULL,
    short_description    VARCHAR(500),
    -- Free-form, not an enum (same reasoning as Authority.authority_type, Phase 3):
    -- the brief gave no fixed vocabulary, and a procedure "type" taxonomy that turns
    -- out wrong is expensive to migrate later. jurisdiction_scope below IS an enum -
    -- that vocabulary is fixed and small.
    procedure_type       VARCHAR(50),
    jurisdiction_scope    VARCHAR(20) NOT NULL,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (jurisdiction_scope IN ('NATIONAL', 'REGIONAL', 'MUNICIPAL', 'MIXED'))
);

CREATE UNIQUE INDEX procedures_code_uq ON procedures (code);
CREATE INDEX procedures_category_idx ON procedures (category_id);
