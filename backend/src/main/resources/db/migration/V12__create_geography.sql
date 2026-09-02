-- Region/City/District per docs/database/DATABASE.md §2. `Region`, not a table named
-- `Voivodeship` (Phase 0 decision, reaffirmed in this phase's brief §9) - region_type
-- carries "VOIVODESHIP" as data so a future country's states/provinces/cantons are
-- just another region_type value, never a new table.
CREATE TABLE regions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL,
    region_type     VARCHAR(30) NOT NULL,
    country_id      UUID NOT NULL REFERENCES countries (id) ON DELETE RESTRICT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from      DATE NOT NULL DEFAULT '1999-01-01',
    valid_to        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX regions_country_code_uq ON regions (country_id, code);

CREATE TABLE cities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL,
    -- Denormalized from region_id - lets "cities in Poland" skip a join to regions,
    -- at the cost of keeping this in sync if a region were ever reassigned to a
    -- different country (never happens in practice; regions don't change country).
    country_id      UUID NOT NULL REFERENCES countries (id) ON DELETE RESTRICT,
    region_id       UUID NOT NULL REFERENCES regions (id) ON DELETE RESTRICT,
    -- This is literally how "Warsaw is the only enabled city in V1" is implemented
    -- (ARCHITECTURE.md §9) - enabling Kraków later is flipping this flag plus seeding
    -- its districts/offices, not a deployment.
    active          BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from      DATE NOT NULL DEFAULT '1999-01-01',
    valid_to        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX cities_region_code_uq ON cities (region_id, code);
CREATE INDEX cities_country_idx ON cities (country_id);

CREATE TABLE districts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL,
    city_id         UUID NOT NULL REFERENCES cities (id) ON DELETE RESTRICT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from      DATE NOT NULL DEFAULT '1999-01-01',
    valid_to        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX districts_city_code_uq ON districts (city_id, code);
