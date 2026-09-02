-- OfficialSource (docs/database/DATABASE.md §3) - every published legal-content item
-- must trace to at least one of these (brief §25). source_type is a fixed, closed
-- vocabulary (brief §23: never BLOG/REDDIT/LAW_FIRM for a source backing production
-- legal requirements - those may inform research but are never cited here).
CREATE TABLE official_sources (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authority_id          UUID REFERENCES authorities (id) ON DELETE RESTRICT,
    title                 VARCHAR(300) NOT NULL,
    -- Structurally validated (http/https only, reasonable length - brief §61) at the
    -- database level as a defense-in-depth backstop; the primary validation is
    -- application-level (OfficialSourceService), which gives a clear ApiError instead
    -- of a raw constraint-violation message.
    source_url            VARCHAR(500) NOT NULL,
    jurisdiction_id       UUID REFERENCES jurisdictions (id) ON DELETE RESTRICT,
    language              VARCHAR(5),
    source_type           VARCHAR(30) NOT NULL,
    publication_date      DATE,
    effective_from        DATE,
    effective_to          DATE,
    last_checked_at       TIMESTAMPTZ,
    last_verified_at      TIMESTAMPTZ,
    verification_status   VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    -- Change-detection metadata only (brief §50) - "content may have changed," never
    -- itself evidence of legal review. No crawler exists yet to populate this
    -- automatically; a future Phase can add one without a schema change.
    content_hash          VARCHAR(128),
    notes                 TEXT,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (source_url ~ '^https?://'),
    CHECK (source_type IN (
        'LEGISLATION', 'GOVERNMENT_GUIDANCE', 'OFFICIAL_SERVICE_PAGE', 'OFFICIAL_FORM',
        'OFFICIAL_FEE_SCHEDULE', 'OFFICIAL_NOTICE', 'OTHER_OFFICIAL'
    )),
    CHECK (verification_status IN ('DRAFT', 'VERIFIED', 'NEEDS_REVIEW', 'OUTDATED', 'ARCHIVED')),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE INDEX official_sources_verification_status_idx ON official_sources (verification_status);
CREATE INDEX official_sources_jurisdiction_idx ON official_sources (jurisdiction_id);

CREATE TABLE source_verifications (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    official_source_id       UUID NOT NULL REFERENCES official_sources (id) ON DELETE CASCADE,
    checked_at                TIMESTAMPTZ NOT NULL,
    checked_by                UUID REFERENCES users (id) ON DELETE SET NULL,
    status                    VARCHAR(20) NOT NULL,
    notes                     TEXT,
    observed_hash             VARCHAR(128),
    change_detected           BOOLEAN NOT NULL DEFAULT FALSE,
    previous_verification_id  UUID REFERENCES source_verifications (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('DRAFT', 'VERIFIED', 'NEEDS_REVIEW', 'OUTDATED', 'ARCHIVED'))
);

CREATE INDEX source_verifications_source_idx ON source_verifications (official_source_id, checked_at);
