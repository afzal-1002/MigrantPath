-- UserCase + snapshot revisions + step/document/fee checklist items + event history
-- (Phase 8, ADR-011, docs/cases/). A UserCase snapshots the Procedure content active at
-- creation time (or at the last explicit upgrade) - it never silently tracks whatever
-- the Procedure/Rule/Threshold content happens to be *now* (brief §2). Snapshot content
-- lives as real rows scoped to one revision, not a JSONB blob (brief §7's hybrid
-- recommendation, simplified: the relational rows already fully capture the snapshot,
-- so a redundant parallel JSONB copy that could drift was deliberately not added - see
-- docs/cases/CASE_SNAPSHOT_POLICY.md).
CREATE TABLE user_cases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- One case per Recommendation (brief §53/§77) - the idempotency/duplicate-prevention
    -- guarantee lives here, not in application code alone.
    recommendation_id   UUID NOT NULL REFERENCES recommendations (id) ON DELETE RESTRICT,
    assessment_id       UUID NOT NULL REFERENCES assessments (id) ON DELETE RESTRICT,
    procedure_id        UUID NOT NULL REFERENCES procedures (id) ON DELETE RESTRICT,
    -- The revision currently shown to the user (the "active" snapshot) - historical
    -- revisions remain in user_case_snapshot_revisions/their item rows untouched.
    current_revision_id UUID,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at        TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    lock_version        BIGINT NOT NULL DEFAULT 0,
    CHECK (status IN (
        'DRAFT', 'PREPARING', 'READY_TO_SUBMIT', 'SUBMITTED', 'WAITING',
        'ADDITIONAL_DOCUMENTS_REQUIRED', 'DECISION_RECEIVED', 'APPROVED', 'REJECTED',
        'APPEAL', 'COMPLETED', 'CANCELLED'
    ))
);

CREATE UNIQUE INDEX user_cases_recommendation_uq ON user_cases (recommendation_id);
CREATE INDEX user_cases_user_status_idx ON user_cases (user_id, status);
CREATE INDEX user_cases_user_updated_idx ON user_cases (user_id, updated_at DESC);

-- The immutable pointer a revision pins (brief §6/§101): exactly which ProcedureVersion,
-- and which date, the snapshot below was built from. Never edited once created - an
-- upgrade always inserts revision_number + 1, never mutates an existing revision
-- (brief §32/§105).
CREATE TABLE user_case_snapshot_revisions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_case_id             UUID NOT NULL REFERENCES user_cases (id) ON DELETE CASCADE,
    revision_number          INT NOT NULL,
    procedure_version_id     UUID NOT NULL REFERENCES procedure_versions (id) ON DELETE RESTRICT,
    evaluation_date          DATE NOT NULL,
    snapshot_schema_version  INT NOT NULL DEFAULT 1,
    reason                   VARCHAR(20) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               UUID REFERENCES users (id) ON DELETE SET NULL,
    previous_revision_id     UUID REFERENCES user_case_snapshot_revisions (id) ON DELETE SET NULL,
    CHECK (reason IN ('INITIAL', 'UPGRADE'))
);

CREATE UNIQUE INDEX user_case_snapshot_revisions_case_number_uq
    ON user_case_snapshot_revisions (user_case_id, revision_number);

ALTER TABLE user_cases
    ADD CONSTRAINT user_cases_current_revision_fkey
    FOREIGN KEY (current_revision_id) REFERENCES user_case_snapshot_revisions (id) ON DELETE SET NULL;

-- Personal, user-specific operational state (brief §9) - never mutates the Procedure's
-- own ProcedureStep/StepVersion rows. source_procedure_step_id is the *stable identity*
-- (survives wording changes across ProcedureVersions, brief §29) used to match an item
-- across snapshot revisions during an upgrade (docs/cases/REQUIREMENT_CHANGE_POLICY.md);
-- source_step_version_id pins exactly which content snapshot was shown.
CREATE TABLE user_case_steps (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_revision_id        UUID NOT NULL REFERENCES user_case_snapshot_revisions (id) ON DELETE CASCADE,
    source_procedure_step_id    UUID NOT NULL REFERENCES procedure_steps (id) ON DELETE RESTRICT,
    source_step_version_id      UUID NOT NULL REFERENCES step_versions (id) ON DELETE RESTRICT,
    stable_code                 VARCHAR(50) NOT NULL,
    title_snapshot               VARCHAR(300) NOT NULL,
    description_snapshot         TEXT,
    detailed_instructions_snapshot TEXT,
    step_type                    VARCHAR(30) NOT NULL,
    sort_order                   INT NOT NULL,
    mandatory                    BOOLEAN NOT NULL DEFAULT TRUE,
    status                       VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    completed_at                 TIMESTAMPTZ,
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'BLOCKED', 'NOT_APPLICABLE'))
);

CREATE INDEX user_case_steps_revision_sort_idx ON user_case_steps (snapshot_revision_id, sort_order);
CREATE UNIQUE INDEX user_case_steps_revision_code_uq ON user_case_steps (snapshot_revision_id, stable_code);

CREATE TABLE user_case_documents (
    id                                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_revision_id                  UUID NOT NULL REFERENCES user_case_snapshot_revisions (id) ON DELETE CASCADE,
    source_document_requirement_id        UUID NOT NULL REFERENCES document_requirements (id) ON DELETE RESTRICT,
    source_document_requirement_version_id UUID NOT NULL REFERENCES document_requirement_versions (id) ON DELETE RESTRICT,
    stable_code                           VARCHAR(50) NOT NULL,
    name_snapshot                          VARCHAR(300) NOT NULL,
    description_snapshot                   TEXT,
    requirement_type                       VARCHAR(20) NOT NULL,
    applicability                          VARCHAR(20) NOT NULL,
    mandatory                              BOOLEAN NOT NULL DEFAULT FALSE,
    number_of_copies_snapshot              INT,
    original_required_snapshot             BOOLEAN,
    translation_required_snapshot          BOOLEAN,
    sworn_translation_required_snapshot    BOOLEAN,
    apostille_required_snapshot            BOOLEAN,
    legalisation_required_snapshot         BOOLEAN,
    validity_period_description_snapshot   VARCHAR(300),
    content_notes_snapshot                 TEXT,
    -- The user's own free-text note (brief §37) - deliberately a separate column from
    -- content_notes_snapshot (the Procedure content's own notes) so the two can never be
    -- confused or overwritten by each other.
    user_note                              VARCHAR(1000),
    sort_order                             INT NOT NULL,
    status                                 VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    ready_at                               TIMESTAMPTZ,
    updated_at                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (requirement_type IN ('DEFAULT_REQUIRED', 'CONDITIONAL', 'INFORMATIONAL')),
    CHECK (applicability IN ('APPLICABLE', 'NEEDS_CONFIRMATION', 'NOT_APPLICABLE')),
    CHECK (status IN ('NOT_STARTED', 'MISSING', 'IN_PROGRESS', 'READY', 'NEEDS_UPDATE', 'NOT_APPLICABLE'))
);

CREATE INDEX user_case_documents_revision_sort_idx ON user_case_documents (snapshot_revision_id, sort_order);
CREATE UNIQUE INDEX user_case_documents_revision_code_uq ON user_case_documents (snapshot_revision_id, stable_code);

CREATE TABLE user_case_fees (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_revision_id           UUID NOT NULL REFERENCES user_case_snapshot_revisions (id) ON DELETE CASCADE,
    source_fee_id                  UUID NOT NULL REFERENCES fees (id) ON DELETE RESTRICT,
    source_fee_version_id          UUID NOT NULL REFERENCES fee_versions (id) ON DELETE RESTRICT,
    stable_code                    VARCHAR(50) NOT NULL,
    fee_type                       VARCHAR(30) NOT NULL,
    amount_snapshot                 NUMERIC(10, 2) NOT NULL,
    currency_snapshot               VARCHAR(3) NOT NULL,
    description_snapshot            VARCHAR(500),
    payment_instructions_snapshot   TEXT,
    sort_order                      INT NOT NULL,
    status                          VARCHAR(20) NOT NULL DEFAULT 'NOT_PAID',
    paid_at                         TIMESTAMPTZ,
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('NOT_PAID', 'PAID', 'NOT_APPLICABLE', 'UNKNOWN'))
);

CREATE INDEX user_case_fees_revision_sort_idx ON user_case_fees (snapshot_revision_id, sort_order);
CREATE UNIQUE INDEX user_case_fees_revision_code_uq ON user_case_fees (snapshot_revision_id, stable_code);

-- Append-only user-facing timeline (brief §24/§25/§82) - never edited after insert.
CREATE TABLE user_case_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_case_id   UUID NOT NULL REFERENCES user_cases (id) ON DELETE CASCADE,
    event_type     VARCHAR(40) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    actor_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    -- Small, non-sensitive metadata only (brief §83) - e.g. "PREPARING -> READY_TO_SUBMIT"
    -- or a stable item code, never a raw answer/note value.
    metadata       VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (event_type IN (
        'CASE_CREATED', 'CASE_STATUS_CHANGED', 'STEP_COMPLETED', 'STEP_REOPENED',
        'DOCUMENT_STATUS_CHANGED', 'FEE_STATUS_CHANGED', 'REQUIREMENTS_UPDATE_DETECTED',
        'CASE_UPDATED_TO_NEW_VERSION', 'CASE_CANCELLED'
    ))
);

CREATE INDEX user_case_events_case_occurred_idx ON user_case_events (user_case_id, occurred_at DESC);
