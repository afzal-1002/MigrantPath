-- ProcedureStep (identity) / StepVersion (docs/database/DATABASE.md §3, brief §12-14).
-- Kept as two tables, not folded into ProcedureVersion directly: DATABASE.md §8's
-- future UserCaseStep sketch already references a stable procedure_step_id (survives
-- wording changes across versions) *and* a pinned step_version_id (what was actually
-- shown) - collapsing the two now would have to be undone when Phase 8 arrives.
CREATE TABLE procedure_steps (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    procedure_id   UUID NOT NULL REFERENCES procedures (id) ON DELETE RESTRICT,
    stable_code    VARCHAR(50) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX procedure_steps_procedure_code_uq ON procedure_steps (procedure_id, stable_code);

CREATE TABLE step_versions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    procedure_step_id      UUID NOT NULL REFERENCES procedure_steps (id) ON DELETE RESTRICT,
    procedure_version_id   UUID NOT NULL REFERENCES procedure_versions (id) ON DELETE CASCADE,
    title                  VARCHAR(300) NOT NULL,
    description            TEXT,
    detailed_instructions  TEXT,
    step_type              VARCHAR(30) NOT NULL,
    -- Every step within one ProcedureVersion has a distinct sort_order (brief §14: not
    -- ambiguous, never ordered by database id).
    sort_order             INT NOT NULL,
    mandatory              BOOLEAN NOT NULL DEFAULT TRUE,
    online_available       BOOLEAN,
    requires_appointment   BOOLEAN,
    expected_user_action   TEXT,
    -- Content-overlay hook (brief §112-114): NULL means "inherits the parent
    -- ProcedureVersion's own jurisdiction"; set only when a specific step's content
    -- genuinely belongs to a narrower jurisdiction (e.g. a NATIONAL procedure's
    -- Warsaw-specific "where to submit" step).
    jurisdiction_id        UUID REFERENCES jurisdictions (id) ON DELETE RESTRICT,
    CHECK (step_type IN (
        'INFORMATION', 'PREPARATION', 'DOCUMENT', 'PAYMENT', 'ONLINE_SUBMISSION',
        'IN_PERSON_SUBMISSION', 'APPOINTMENT', 'BIOMETRICS', 'WAITING',
        'ADDITIONAL_DOCUMENTS', 'DECISION', 'COLLECTION', 'OTHER'
    ))
);

-- A new ProcedureVersion always needs its own StepVersion rows - no silent fallback to
-- a prior version's steps (IMPLEMENTATION_PLAN.md 4.3's DoD) - this unique constraint is
-- what makes that structural, not just a convention: the same procedure_step_id can
-- appear at most once per procedure_version_id.
CREATE UNIQUE INDEX step_versions_version_step_uq ON step_versions (procedure_version_id, procedure_step_id);
CREATE UNIQUE INDEX step_versions_version_sort_order_uq ON step_versions (procedure_version_id, sort_order);
CREATE INDEX step_versions_procedure_version_idx ON step_versions (procedure_version_id, sort_order);
