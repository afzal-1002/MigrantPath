-- CountryGroup/CountryGroupMembership per docs/database/DATABASE.md §2. Membership is
-- deliberately time-bounded (valid_from/valid_to), not a boolean flag on countries -
-- the UK's EU membership ending in 2020 is exactly the case a static flag would get
-- wrong for any pre-2020 evaluation. THIRD_COUNTRY is deliberately NOT a group here -
-- see docs/architecture/ADR/ADR-006-country-classification.md for why it's derived,
-- not stored.
CREATE TABLE country_groups (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    -- LEGAL: a real EU-law/treaty category (EU membership, EEA, EFTA, Schengen).
    -- CONVENIENCE: a useful aggregate this application defines for its own purposes
    -- (e.g. EU_EEA_SWISS) that is not itself a legal category - see brief §45.
    group_type    VARCHAR(20) NOT NULL CHECK (group_type IN ('LEGAL', 'CONVENIENCE')),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX country_groups_code_uq ON country_groups (code);

CREATE TABLE country_group_memberships (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id        UUID NOT NULL REFERENCES countries (id) ON DELETE RESTRICT,
    country_group_id  UUID NOT NULL REFERENCES country_groups (id) ON DELETE RESTRICT,
    valid_from        DATE NOT NULL,
    valid_to          DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

-- A country can only belong to the same group once per validity period start - this
-- doesn't prevent every conceivable overlap (that would need an exclusion constraint
-- on a date range, judged not worth the added complexity for reference data that
-- changes on the order of "once a decade" - brief §18) but does catch the common
-- mistake of seeding the same membership row twice.
CREATE UNIQUE INDEX country_group_memberships_uq ON country_group_memberships (country_id, country_group_id, valid_from);

-- The Active-Version-Predicate-style temporal lookup ("who is in group X as of date
-- D") is this index's primary use case.
CREATE INDEX country_group_memberships_lookup_idx ON country_group_memberships (country_group_id, valid_from, valid_to);
CREATE INDEX country_group_memberships_country_idx ON country_group_memberships (country_id);
