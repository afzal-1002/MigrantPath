-- Country is pure reference data (docs/database/DATABASE.md §2) - nationality is
-- never conflated with legal classification here; this table only records the ISO
-- identity of a country. See docs/reference/REFERENCE_DATA_SOURCES.md for where the
-- seed data (V8) comes from.
CREATE TABLE countries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- VARCHAR, not CHAR: these are identifiers compared for equality, not
    -- fixed-width display fields - CHAR's space-padding semantics buy nothing here
    -- and would only cause friction (e.g. Hibernate maps a Java String to VARCHAR by
    -- default, not CHAR).
    code             VARCHAR(2) NOT NULL,
    alpha3_code      VARCHAR(3),
    numeric_code     VARCHAR(3),
    canonical_name   VARCHAR(200) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    display_order    INT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX countries_code_uq ON countries (code);
CREATE UNIQUE INDEX countries_alpha3_code_uq ON countries (alpha3_code) WHERE alpha3_code IS NOT NULL;
