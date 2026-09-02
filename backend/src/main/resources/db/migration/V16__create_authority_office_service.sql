-- Authority (an institution with a legal mandate) vs Office (a physical place that
-- institution operates) - docs/database/DATABASE.md §2. Kept as plain mutable rows
-- with valid_from/valid_to (not the full identity+version pattern legal content
-- uses) - an office's address is an operational fact admins correct, not a legal
-- position needing draft/review workflow (DATABASE.md §0's rationale for Office
-- applies identically here).
CREATE TABLE authorities (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  VARCHAR(50) NOT NULL,
    canonical_name        VARCHAR(300) NOT NULL,
    -- Free-form, not an enum: the brief gave no fixed authority-type vocabulary, and
    -- inventing one prematurely (brief §21) risks being wrong for a future country's
    -- institutional structure.
    authority_type        VARCHAR(50) NOT NULL,
    jurisdiction_id       UUID NOT NULL REFERENCES jurisdictions (id) ON DELETE RESTRICT,
    parent_authority_id   UUID REFERENCES authorities (id) ON DELETE RESTRICT,
    official_website      VARCHAR(300),
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from            DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to              DATE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX authorities_code_uq ON authorities (code);
CREATE INDEX authorities_jurisdiction_idx ON authorities (jurisdiction_id);
CREATE INDEX authorities_parent_idx ON authorities (parent_authority_id);

CREATE TABLE service_types (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX service_types_code_uq ON service_types (code);

CREATE TABLE offices (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  VARCHAR(50) NOT NULL,
    authority_id          UUID NOT NULL REFERENCES authorities (id) ON DELETE RESTRICT,
    canonical_name        VARCHAR(300) NOT NULL,
    street                VARCHAR(200),
    building_number       VARCHAR(20),
    postal_code           VARCHAR(10),
    city_id               UUID NOT NULL REFERENCES cities (id) ON DELETE RESTRICT,
    district_id           UUID REFERENCES districts (id) ON DELETE RESTRICT,
    phone                 VARCHAR(50),
    email                 VARCHAR(200),
    website               VARCHAR(300),
    appointment_required  BOOLEAN,
    booking_url           VARCHAR(300),
    -- Genuinely irregular per-office schedules (varies day-to-day, can have seasonal/
    -- holiday exceptions) - a justified JSONB use (DATABASE.md §2's Office entry makes
    -- the same call) rather than a fixed set of from/to columns per weekday that most
    -- offices wouldn't uniformly need. Deliberately not populated in Phase 3 (brief
    -- §49 - opening hours are operational data that changes frequently; if included
    -- at all it must carry provenance, and no Phase 3 source was verified specifically
    -- for current hours, only for the address/contact facts already cited).
    opening_hours         JSONB,
    source_url            VARCHAR(500),
    last_verified_at      TIMESTAMPTZ,
    notes                 TEXT,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from            DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to              DATE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX offices_code_uq ON offices (code);
CREATE INDEX offices_city_idx ON offices (city_id);
CREATE INDEX offices_district_idx ON offices (district_id);
CREATE INDEX offices_authority_idx ON offices (authority_id);

-- Routing/reference information only (brief §14) - never confused with Procedure
-- logic, which belongs to Phase 4+.
CREATE TABLE office_services (
    office_id        UUID NOT NULL REFERENCES offices (id) ON DELETE CASCADE,
    service_type_id  UUID NOT NULL REFERENCES service_types (id) ON DELETE RESTRICT,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    notes            TEXT,
    PRIMARY KEY (office_id, service_type_id)
);
