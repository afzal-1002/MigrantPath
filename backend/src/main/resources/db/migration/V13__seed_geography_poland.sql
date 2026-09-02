-- All 16 Polish voivodeships (est. 1999-01-01 reform - see
-- docs/reference/REFERENCE_DATA_SOURCES.md) are seeded now: this is small, stable
-- reference data and improves future expansion (brief §26 recommendation), even
-- though only Mazowieckie/Warsaw carry operational (office/authority) data in V1.
INSERT INTO regions (code, canonical_name, region_type, country_id, valid_from)
SELECT v.code, v.canonical_name, 'VOIVODESHIP', c.id, '1999-01-01'
FROM (VALUES
    ('DOLNOSLASKIE', 'Dolnośląskie'),
    ('KUJAWSKO_POMORSKIE', 'Kujawsko-Pomorskie'),
    ('LUBELSKIE', 'Lubelskie'),
    ('LUBUSKIE', 'Lubuskie'),
    ('LODZKIE', 'Łódzkie'),
    ('MALOPOLSKIE', 'Małopolskie'),
    ('MAZOWIECKIE', 'Mazowieckie'),
    ('OPOLSKIE', 'Opolskie'),
    ('PODKARPACKIE', 'Podkarpackie'),
    ('PODLASKIE', 'Podlaskie'),
    ('POMORSKIE', 'Pomorskie'),
    ('SLASKIE', 'Śląskie'),
    ('SWIETOKRZYSKIE', 'Świętokrzyskie'),
    ('WARMINSKO_MAZURSKIE', 'Warmińsko-Mazurskie'),
    ('WIELKOPOLSKIE', 'Wielkopolskie'),
    ('ZACHODNIOPOMORSKIE', 'Zachodniopomorskie')
) AS v(code, canonical_name)
JOIN countries c ON c.code = 'PL';

-- Warsaw is the only active city in V1 (ARCHITECTURE.md §9) - belongs to Mazowieckie.
INSERT INTO cities (code, canonical_name, country_id, region_id, active, valid_from)
SELECT 'WARSAW', 'Warszawa', c.id, r.id, TRUE, '1999-01-01'
FROM countries c
JOIN regions r ON r.country_id = c.id AND r.code = 'MAZOWIECKIE'
WHERE c.code = 'PL';

-- The 18 official districts (dzielnice) of Warsaw. Polish diacritics preserved in
-- canonical_name (brief §62 - never ASCII-normalize display names); codes are
-- ASCII-safe business identifiers.
INSERT INTO districts (code, canonical_name, city_id, valid_from)
SELECT v.code, v.canonical_name, ci.id, '2002-01-01'
FROM (VALUES
    ('BEMOWO', 'Bemowo'),
    ('BIALOLEKA', 'Białołęka'),
    ('BIELANY', 'Bielany'),
    ('MOKOTOW', 'Mokotów'),
    ('OCHOTA', 'Ochota'),
    ('PRAGA_POLUDNIE', 'Praga-Południe'),
    ('PRAGA_POLNOC', 'Praga-Północ'),
    ('REMBERTOW', 'Rembertów'),
    ('SRODMIESCIE', 'Śródmieście'),
    ('TARGOWEK', 'Targówek'),
    ('URSUS', 'Ursus'),
    ('URSYNOW', 'Ursynów'),
    ('WAWER', 'Wawer'),
    ('WESOLA', 'Wesoła'),
    ('WILANOW', 'Wilanów'),
    ('WLOCHY', 'Włochy'),
    ('WOLA', 'Wola'),
    ('ZOLIBORZ', 'Żoliborz')
) AS v(code, canonical_name)
JOIN cities ci ON ci.code = 'WARSAW';
