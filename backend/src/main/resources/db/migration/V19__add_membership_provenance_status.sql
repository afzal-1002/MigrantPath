-- Post-Phase-3-approval fix: V11's own migration comment already flagged pre-2000
-- accession dates as "approximate... compiled from general knowledge... not a single
-- authoritative dataset", but that fact only lived in a comment - nothing made it
-- queryable. This makes it a real, visible column so a future rule evaluation can
-- require VERIFIED provenance for a legally-significant classification where
-- appropriate, instead of silently trusting an unverified historical date.
--
-- Not a re-verification pass: no date value is being researched or corrected here,
-- only the existing rows' confidence being made explicit. See
-- docs/reference/REFERENCE_DATA_SOURCES.md.
ALTER TABLE country_group_memberships
    ADD COLUMN provenance_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED';

ALTER TABLE country_group_memberships
    ADD CONSTRAINT country_group_memberships_provenance_status_check
    CHECK (provenance_status IN ('VERIFIED', 'DRAFT'));

-- The cutoff (2000-01-01) matches V11's own comment ("historical accession dates before
-- roughly 2000 are approximate"). A row's valid_from being pre-2000 is what's marked
-- DRAFT, even where valid_to is a well-known, precise date (e.g. the UK's EU membership
-- ending 2020-01-31 is exact - it's the 1973-01-01 start date this flags as approximate).
UPDATE country_group_memberships
SET provenance_status = 'DRAFT'
WHERE valid_from < '2000-01-01';
