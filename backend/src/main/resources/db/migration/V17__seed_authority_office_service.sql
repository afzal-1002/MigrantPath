-- Conservative, verified-only seeding (brief §48): three authorities whose identity
-- is well-established, but only ONE office record - the one address this phase
-- actually re-verified against a primary source (see
-- docs/reference/REFERENCE_DATA_SOURCES.md). Warsaw district-level office routing for
-- PESEL/meldunek/driving-licence (genuinely complex, applicant-dependent - brief §19)
-- is deliberately deferred to Phase 10 rather than guessed at here.
INSERT INTO authorities (code, canonical_name, authority_type, jurisdiction_id, official_website)
SELECT 'UDSC', 'Urząd do Spraw Cudzoziemców (Office for Foreigners)', 'NATIONAL_AGENCY', j.id, 'https://udsc.gov.pl'
FROM jurisdictions j WHERE j.code = 'PL';

INSERT INTO authorities (code, canonical_name, authority_type, jurisdiction_id, official_website)
SELECT 'MAZOWIECKIE_VOIVODESHIP_OFFICE', 'Mazowiecki Urząd Wojewódzki w Warszawie', 'REGIONAL_OFFICE', j.id, 'https://www.gov.pl/web/uw-mazowiecki'
FROM jurisdictions j WHERE j.code = 'PL_MAZOWIECKIE';

INSERT INTO authorities (code, canonical_name, authority_type, jurisdiction_id, official_website)
SELECT 'WARSAW_CITY_HALL', 'Miasto Stołeczne Warszawa (City of Warsaw)', 'MUNICIPAL_GOVERNMENT', j.id, 'https://warszawa19115.pl'
FROM jurisdictions j WHERE j.code = 'PL_MAZOWIECKIE_WARSAW';

INSERT INTO service_types (code, name, description) VALUES
    ('PESEL', 'PESEL number assignment', 'Assignment of a Polish national identification number to a foreigner.'),
    ('MELDUNEK', 'Address registration (zameldowanie)', 'Temporary or permanent residence address registration.'),
    ('DRIVING_LICENCE', 'Foreign driving licence exchange', 'Exchange of a foreign driving licence for a Polish one.'),
    ('IMMIGRATION_INFORMATION', 'Immigration/residence permit information and processing', 'Temporary/permanent residence permits, EU citizen registration, and related processing.');

-- Verified directly against https://www.gov.pl/web/uw-mazowiecki/wydzial-spraw-cudzoziemcow
-- on 2026-09-02 (see docs/reference/REFERENCE_DATA_SOURCES.md) - this is the one
-- office record in this migration with source_url/last_verified_at populated,
-- reflecting that verification.
INSERT INTO offices (code, authority_id, canonical_name, street, building_number, postal_code, city_id, phone, email, source_url, last_verified_at, notes)
SELECT
    'MAZOWIECKIE_WSC_MARSZALKOWSKA',
    a.id,
    'Wydział Spraw Cudzoziemców (Department for Foreigners'' Affairs)',
    'ul. Marszałkowska',
    '3/5',
    '00-624',
    ci.id,
    '22 695 65 65',
    'wsc@mazowieckie.pl',
    'https://www.gov.pl/web/uw-mazowiecki/wydzial-spraw-cudzoziemcow',
    now(),
    'Other addresses (e.g. Krucza 5/11, Plac Bankowy 3/5) appear in secondary sources for specific sub-services (fingerprints, card collection, general MUW information point) but were not verified in this pass - confirm with the applicant''s specific case type before routing.'
FROM authorities a
JOIN cities ci ON ci.code = 'WARSAW'
WHERE a.code = 'MAZOWIECKIE_VOIVODESHIP_OFFICE';

INSERT INTO office_services (office_id, service_type_id)
SELECT o.id, st.id
FROM offices o
JOIN service_types st ON st.code = 'IMMIGRATION_INFORMATION'
WHERE o.code = 'MAZOWIECKIE_WSC_MARSZALKOWSKA';
