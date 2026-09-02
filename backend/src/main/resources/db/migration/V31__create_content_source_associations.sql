-- Content -> OfficialSource provenance (docs/database/DATABASE.md §7, brief §25-26).
-- Five small, real-FK join tables rather than one polymorphic
-- content_type/content_id association: each versioned content table has a genuinely
-- different shape, and dedicated FKs give real referential integrity and simple JPA
-- mapping - "maintainability over clever polymorphic SQL" (brief §25's own preference,
-- and consistent with how this codebase already treats AdminReview/AuditLog's
-- polymorphic columns in DATABASE.md §9 as an accepted trade-off only for logs, never
-- for content that needs real integrity).
--
-- role lets one item cite more than one source with different weight (brief §26) -
-- optional in the sense that PRIMARY is a safe default, not that it's nullable.
CREATE TABLE procedure_version_sources (
    procedure_version_id  UUID NOT NULL REFERENCES procedure_versions (id) ON DELETE CASCADE,
    official_source_id    UUID NOT NULL REFERENCES official_sources (id) ON DELETE RESTRICT,
    role                   VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY (procedure_version_id, official_source_id),
    CHECK (role IN ('PRIMARY', 'SUPPORTING', 'OPERATIONAL'))
);

CREATE TABLE step_version_sources (
    step_version_id      UUID NOT NULL REFERENCES step_versions (id) ON DELETE CASCADE,
    official_source_id   UUID NOT NULL REFERENCES official_sources (id) ON DELETE RESTRICT,
    role                  VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY (step_version_id, official_source_id),
    CHECK (role IN ('PRIMARY', 'SUPPORTING', 'OPERATIONAL'))
);

CREATE TABLE document_requirement_version_sources (
    document_requirement_version_id  UUID NOT NULL REFERENCES document_requirement_versions (id) ON DELETE CASCADE,
    official_source_id                UUID NOT NULL REFERENCES official_sources (id) ON DELETE RESTRICT,
    role                               VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY (document_requirement_version_id, official_source_id),
    CHECK (role IN ('PRIMARY', 'SUPPORTING', 'OPERATIONAL'))
);

CREATE TABLE fee_version_sources (
    fee_version_id       UUID NOT NULL REFERENCES fee_versions (id) ON DELETE CASCADE,
    official_source_id   UUID NOT NULL REFERENCES official_sources (id) ON DELETE RESTRICT,
    role                  VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY (fee_version_id, official_source_id),
    CHECK (role IN ('PRIMARY', 'SUPPORTING', 'OPERATIONAL'))
);

CREATE TABLE threshold_version_sources (
    threshold_version_id  UUID NOT NULL REFERENCES threshold_versions (id) ON DELETE CASCADE,
    official_source_id    UUID NOT NULL REFERENCES official_sources (id) ON DELETE RESTRICT,
    role                   VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY (threshold_version_id, official_source_id),
    CHECK (role IN ('PRIMARY', 'SUPPORTING', 'OPERATIONAL'))
);
