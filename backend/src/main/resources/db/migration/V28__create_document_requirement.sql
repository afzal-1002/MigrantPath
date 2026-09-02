-- DocumentRequirement (identity) / DocumentRequirementVersion, same identity+version
-- split as ProcedureStep/StepVersion, for the same DATABASE.md §8 snapshot-readiness
-- reason. No condition_rule_id FK here (brief §16 explicitly overrides
-- IMPLEMENTATION_PLAN.md 4.4's original placeholder-FK suggestion: "avoid speculative
-- foreign keys to tables that do not exist" - Phase 6's Rule table doesn't exist yet).
-- requirement_type carries the forward-compatible signal instead: CONDITIONAL marks
-- "a future Rule will decide," without a dangling reference to it.
CREATE TABLE document_requirements (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    procedure_id   UUID NOT NULL REFERENCES procedures (id) ON DELETE RESTRICT,
    stable_code    VARCHAR(50) NOT NULL,
    document_type_id UUID REFERENCES document_types (id) ON DELETE RESTRICT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX document_requirements_procedure_code_uq ON document_requirements (procedure_id, stable_code);

CREATE TABLE document_requirement_versions (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_requirement_id       UUID NOT NULL REFERENCES document_requirements (id) ON DELETE RESTRICT,
    procedure_version_id          UUID NOT NULL REFERENCES procedure_versions (id) ON DELETE CASCADE,
    name                          VARCHAR(300) NOT NULL,
    description                   TEXT,
    -- Brief §15/§16: DEFAULT_REQUIRED (applies to everyone), CONDITIONAL (a future
    -- Phase 6 Rule decides whether it applies to a given user - not evaluated here),
    -- INFORMATIONAL (mentioned by the official source but not itself a submission
    -- requirement, e.g. "bring a copy for your own records").
    requirement_type              VARCHAR(20) NOT NULL,
    required_by_default           BOOLEAN NOT NULL DEFAULT TRUE,
    number_of_copies              INT,
    original_required             BOOLEAN,
    copy_required                 BOOLEAN,
    -- Booleans only where the source genuinely states a fixed yes/no (brief §17) -
    -- validity_period stays free text below because "not older than 3 months" carries
    -- meaning a structured DURATION type would flatten without gaining anything a
    -- human reviewer couldn't already get from the text.
    translation_required          BOOLEAN,
    sworn_translation_required    BOOLEAN,
    apostille_required            BOOLEAN,
    legalisation_required         BOOLEAN,
    validity_period_description   VARCHAR(300),
    notes                         TEXT,
    sort_order                    INT NOT NULL,
    CHECK (requirement_type IN ('DEFAULT_REQUIRED', 'CONDITIONAL', 'INFORMATIONAL'))
);

CREATE UNIQUE INDEX document_requirement_versions_version_req_uq
    ON document_requirement_versions (procedure_version_id, document_requirement_id);
CREATE INDEX document_requirement_versions_procedure_version_idx
    ON document_requirement_versions (procedure_version_id, sort_order);
