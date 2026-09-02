-- Jurisdiction is the legal/procedural scope a future Procedure/Rule/Authority
-- operates at (National/Regional/Municipal) - distinct from the Region/City/District
-- geography tables, which record *where a place is*, not *at what legal scope a rule
-- applies* (docs/database/DATABASE.md §2 / ARCHITECTURE.md §9).
--
-- Modeled as a self-referencing tree (parent_jurisdiction_id) per this phase's brief
-- §8, refining Phase 0's original flat-FK sketch: a tree supports arbitrary future
-- depth (a country with country -> state -> province -> city -> district jurisdiction
-- levels doesn't need a new column added to this table), where the flat design would
-- have needed one FK column per level. region_id/city_id are still carried directly
-- (not just derivable by walking parent pointers) so "find the jurisdiction for
-- Warsaw" is a plain indexed lookup, not a recursive CTE - the tree gives traversal,
-- the direct FKs give practical joins; neither replaces the other.
CREATE TABLE jurisdictions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(50) NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    jurisdiction_type       VARCHAR(20) NOT NULL CHECK (jurisdiction_type IN ('NATIONAL', 'REGIONAL', 'MUNICIPAL')),
    parent_jurisdiction_id  UUID REFERENCES jurisdictions (id) ON DELETE RESTRICT,
    country_id              UUID NOT NULL REFERENCES countries (id) ON DELETE RESTRICT,
    region_id               UUID REFERENCES regions (id) ON DELETE RESTRICT,
    city_id                 UUID REFERENCES cities (id) ON DELETE RESTRICT,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from              DATE NOT NULL DEFAULT '1999-01-01',
    valid_to                DATE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK (
        (jurisdiction_type = 'NATIONAL' AND region_id IS NULL AND city_id IS NULL AND parent_jurisdiction_id IS NULL)
        OR (jurisdiction_type = 'REGIONAL' AND region_id IS NOT NULL AND city_id IS NULL AND parent_jurisdiction_id IS NOT NULL)
        OR (jurisdiction_type = 'MUNICIPAL' AND city_id IS NOT NULL AND parent_jurisdiction_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX jurisdictions_code_uq ON jurisdictions (code);
CREATE INDEX jurisdictions_parent_idx ON jurisdictions (parent_jurisdiction_id);
CREATE INDEX jurisdictions_country_idx ON jurisdictions (country_id);
CREATE UNIQUE INDEX jurisdictions_city_uq ON jurisdictions (city_id) WHERE city_id IS NOT NULL;
CREATE UNIQUE INDEX jurisdictions_region_uq ON jurisdictions (region_id) WHERE region_id IS NOT NULL AND jurisdiction_type = 'REGIONAL';
