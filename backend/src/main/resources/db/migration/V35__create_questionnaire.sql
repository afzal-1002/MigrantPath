-- Questionnaire (identity) / QuestionnaireVersion (docs/database/DATABASE.md §4,
-- IMPLEMENTATION_PLAN.md Phase 5). Same identity+version+publication-lifecycle pattern
-- as Procedure/ProcedureVersion (V22/V25) - reuses the exact same PublicationStatus
-- vocabulary and the same btree_gist "no two overlapping PUBLISHED date ranges"
-- exclusion constraint, for the same reason: an in-progress Assessment is bound to one
-- QuestionnaireVersion id forever (brief §4), so exactly one version may be the
-- publicly-resolvable "active" one on any given date.
CREATE TABLE questionnaires (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(300) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX questionnaires_code_uq ON questionnaires (code);

CREATE TABLE questionnaire_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    questionnaire_id UUID NOT NULL REFERENCES questionnaires (id) ON DELETE RESTRICT,
    version_number  INT NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    -- Nullable at the DRAFT stage, same as procedure_versions (V25) - the publish
    -- workflow enforces these are set before allowing PUBLISHED.
    effective_from  DATE,
    effective_to    DATE,
    created_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    submitted_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    approved_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    published_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    submitted_at    TIMESTAMPTZ,
    approved_at     TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_version    BIGINT NOT NULL DEFAULT 0,
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX questionnaire_versions_questionnaire_version_number_uq
    ON questionnaire_versions (questionnaire_id, version_number);
CREATE INDEX questionnaire_versions_questionnaire_status_idx
    ON questionnaire_versions (questionnaire_id, status);
CREATE INDEX questionnaire_versions_effective_idx
    ON questionnaire_versions (effective_from, effective_to);

-- btree_gist is already enabled by V25, but IF NOT EXISTS keeps this migration
-- independently replayable/order-agnostic in a fresh database.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE questionnaire_versions
    ADD CONSTRAINT questionnaire_versions_no_overlapping_published
    EXCLUDE USING gist (
        questionnaire_id WITH =,
        daterange(effective_from, effective_to) WITH &&
    ) WHERE (status = 'PUBLISHED');
