-- The 8 MVP procedure identities from docs/product/PROCEDURE_CATALOGUE.md's "MVP set"
-- (real, already-researched catalogue entries - not invented). jurisdiction_scope
-- matches the catalogue's own jurisdiction tags exactly (5 NATIONAL, 3 MUNICIPAL) -
-- none is MIXED, since none of these 8 has eligibility conditions that themselves vary
-- by region (the catalogue's own definition of when MIXED applies). No published legal
-- content is seeded here - only stable identity. See V36 for the DRAFT-status
-- OfficialSource/ProcedureVersion rows imported from the catalogue's own research.
INSERT INTO procedures (code, category_id, canonical_name, short_description, jurisdiction_scope)
SELECT v.code, c.id, v.canonical_name, v.short_description, v.jurisdiction_scope
FROM (VALUES
    ('TEMP_RESIDENCE_WORK', 'WORK',
     'Temporary residence and work (uniform permit)',
     'Third-country nationals with a Polish job offer/contract, staying more than 3 months',
     'NATIONAL'),
    ('EU_BLUE_CARD', 'WORK',
     'EU Blue Card',
     'Third-country nationals with a highly-qualified job offer above the salary threshold',
     'NATIONAL'),
    ('TEMP_RESIDENCE_STUDY', 'STUDY',
     'Temporary residence for studies',
     'Third-country nationals in full-time studies',
     'NATIONAL'),
    ('FAMILY_REUNIFICATION', 'FAMILY',
     'Temporary residence - family reunification (spouse of Polish citizen)',
     'Third-country spouse of a Polish citizen',
     'NATIONAL'),
    ('EU_RESIDENCE_REGISTRATION', 'EU_FREE_MOVEMENT',
     'EU citizen residence registration',
     'EU/EEA/Swiss citizens staying more than 3 months',
     'NATIONAL'),
    ('PESEL', 'IDENTITY_REGISTRATION',
     'PESEL number assignment',
     'Any foreigner needing a Polish national identification number',
     'MUNICIPAL'),
    ('MELDUNEK', 'IDENTITY_REGISTRATION',
     'Address registration (meldunek)',
     'Foreigners residing at a Warsaw address; deadline varies by citizenship group',
     'MUNICIPAL'),
    ('FOREIGN_DRIVING_LICENCE_EXCHANGE', 'DRIVING',
     'Foreign driving licence exchange',
     'Holders of a foreign driving licence exchanging it for a Polish one',
     'MUNICIPAL')
) AS v(code, category_code, canonical_name, short_description, jurisdiction_scope)
JOIN procedure_categories c ON c.code = v.category_code;
