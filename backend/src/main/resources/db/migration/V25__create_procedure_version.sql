-- ProcedureVersion (docs/database/DATABASE.md §3) - immutable-once-published legal
-- content, snapshotted per version (ADR-004/ADR-007). status/effective_from/
-- effective_to together implement the Active-Version Predicate (DATABASE.md §0) with
-- the same EXCLUSIVE effective_to convention as the rest of legal content (deliberately
-- different from reference data's inclusive valid_to - ADR-006) - see
-- ProcedureVersionRepository's Javadoc for the one authoritative query.
--
-- btree_gist backs the EXCLUDE constraint below - the standard way to enforce "no two
-- rows with the same procedure_id have overlapping date ranges" at the database level,
-- not just in application code (brief §11).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE procedure_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    procedure_id        UUID NOT NULL REFERENCES procedures (id) ON DELETE RESTRICT,
    version_number      INT NOT NULL,
    title               VARCHAR(300) NOT NULL,
    summary             VARCHAR(1000),
    description         TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    -- Nullable at the DRAFT stage (brief §29: "DRAFT content may exist freely") -
    -- ProcedureVersionService enforces these are set before allowing PUBLISHED.
    effective_from      DATE,
    effective_to        DATE,
    jurisdiction_id     UUID REFERENCES jurisdictions (id) ON DELETE RESTRICT,
    change_summary      VARCHAR(1000),
    created_by          UUID REFERENCES users (id) ON DELETE SET NULL,
    submitted_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    approved_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    published_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    submitted_at        TIMESTAMPTZ,
    approved_at         TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Hibernate optimistic-lock column (@Version, brief §60) - a *different* concept
    -- from version_number (the business-visible "Version 1/2/3" a user sees); this
    -- column is invisible outside the persistence layer.
    lock_version        BIGINT NOT NULL DEFAULT 0,
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX procedure_versions_procedure_version_number_uq
    ON procedure_versions (procedure_id, version_number);
CREATE INDEX procedure_versions_procedure_status_idx ON procedure_versions (procedure_id, status);
CREATE INDEX procedure_versions_effective_idx ON procedure_versions (effective_from, effective_to);

-- At most one PUBLISHED version of the same procedure may have an active date range at
-- any given moment (brief §11). Postgres's daterange defaults to `[)` - lower-inclusive,
-- upper-exclusive - which matches this schema's own effective_from <=
-- evaluationDate < effective_to convention exactly, so no explicit bound-type argument
-- is needed. A NULL effective_to means "still open," which daterange(x, NULL)
-- represents correctly as unbounded above.
ALTER TABLE procedure_versions
    ADD CONSTRAINT procedure_versions_no_overlapping_published
    EXCLUDE USING gist (
        procedure_id WITH =,
        daterange(effective_from, effective_to) WITH &&
    ) WHERE (status = 'PUBLISHED');
