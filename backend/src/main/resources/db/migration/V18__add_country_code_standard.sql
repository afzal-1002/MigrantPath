-- Post-Phase-3-approval fix: the seeded 250-row `countries` table was silently mixing
-- 249 officially assigned ISO 3166-1 alpha-2 codes with one user-assigned code (`XK`,
-- Kosovo) that the ISO 3166 Maintenance Agency has never assigned - confirmed against
-- Wikipedia's ISO 3166-1 alpha-2 article, which states "249 current officially assigned
-- codes" and describes XK as a temporary/user-assigned code used by the European
-- Commission, IMF, SWIFT, and Unicode CLDR, among others. See
-- docs/reference/REFERENCE_DATA_SOURCES.md for the full note.
--
-- Kosovo is kept (not removed to force the count to 249) because it's operationally
-- useful for this application (e.g. as a country of citizenship) - it's just now
-- modeled and documented honestly rather than silently presented as an ISO code.
ALTER TABLE countries
    ADD COLUMN code_standard       VARCHAR(20) NOT NULL DEFAULT 'ISO_3166_1',
    ADD COLUMN officially_assigned BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notes               TEXT;

ALTER TABLE countries
    ADD CONSTRAINT countries_code_standard_check
    CHECK (code_standard IN ('ISO_3166_1', 'USER_ASSIGNED'));

UPDATE countries
SET code_standard = 'USER_ASSIGNED',
    officially_assigned = FALSE,
    notes = 'XK is not an officially assigned ISO 3166-1 alpha-2 code - the ISO 3166 '
        || 'Maintenance Agency has never assigned Kosovo a code. XK is a user-assigned/'
        || 'exceptionally-reserved code used as a temporary designation for Kosovo by the '
        || 'European Commission, IMF, SWIFT, Unicode CLDR, and other organizations - the '
        || 'same convention the source mledoze/countries dataset follows. The alpha-3 '
        || '(''UNK'') and absent numeric code are equally unofficial placeholders, not ISO '
        || '3166-1 assignments. Kept (not removed) because Kosovo is operationally useful '
        || 'for this application - see docs/reference/REFERENCE_DATA_SOURCES.md.'
WHERE code = 'XK';
