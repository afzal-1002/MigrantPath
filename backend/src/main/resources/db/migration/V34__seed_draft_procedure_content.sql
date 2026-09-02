-- Imports the real, already-researched DRAFT findings from
-- docs/product/PROCEDURE_CATALOGUE.md's "MVP procedure source records" (brief §52) -
-- real official URLs, real jurisdiction tags, honest uncertainty notes copied
-- verbatim-in-spirit from that document. Nothing here is invented: no step, no
-- document requirement, no fee is seeded, because the catalogue itself does not yet
-- have verified step-by-step/document-by-document content for these procedures - an
-- empty steps/documents list is the honest representation of "not yet captured," not a
-- placeholder pretending otherwise (brief §52's "never transformed into invented
-- requirements").
--
-- EU_BLUE_CARD is deliberately EXCLUDED from this seed: the catalogue's only recorded
-- source for it (dudkowiak.com) is a law firm, not an official domain (brief §23
-- explicitly forbids citing one as an OfficialSource) - the catalogue itself flags the
-- real UDSC/MOS page as "not yet directly captured" (its own action item for Phase 10).
-- The EU_BLUE_CARD Procedure identity (V23) still exists; it simply has no DRAFT
-- version or source yet.
--
-- Every version below is DRAFT and stays DRAFT - none is published by this migration
-- (brief §29/§53). effective_from/effective_to are NULL until Phase 10 sets them as
-- part of an actual publish.
INSERT INTO procedure_versions (procedure_id, version_number, title, summary, description, status, jurisdiction_id, change_summary)
SELECT p.id, 1, p.canonical_name, p.short_description, v.description, 'DRAFT', j.id,
       'Imported from docs/product/PROCEDURE_CATALOGUE.md research pass (2026-09-01) - DRAFT only, never published.'
FROM (VALUES
    ('TEMP_RESIDENCE_WORK', 'PL',
     'Some sources report a shift toward mandatory online submission via "MOS 2.0" during 2026 - exact effective date and scope not yet confirmed against the primary text.'),
    ('TEMP_RESIDENCE_STUDY', 'PL',
     'Permit-duration rules (15 months in the first year, etc.) need verification against the primary text; no specific "sufficient funds" figure found yet.'),
    ('FAMILY_REUNIFICATION', 'PL',
     'A secondary source states applications must go through MOS electronically only - needs primary-source confirmation; no specific income figure found for the "stable and regular income" test.'),
    ('EU_RESIDENCE_REGISTRATION', 'PL',
     'The "sufficient resources" test for economically inactive EU citizens has no specific figure identified in this pass - likely case-by-case, to be confirmed.'),
    ('PESEL', 'PL_MAZOWIECKIE_WARSAW',
     'Which specific Warsaw district office applies for a given applicant depends on individual circumstances beyond "no registerable address" - needs a fuller routing rule before ProcedureVersionOffice rows are added.'),
    ('MELDUNEK', 'PL_MAZOWIECKIE_WARSAW',
     'Exact document list and the online-vs-in-person split for EU vs. non-EU applicants needs line-by-line confirmation; the 4-day vs. 30-day deadline split is DRAFT-confirmed by two independent secondary mentions but not yet read from the primary legal text.'),
    ('FOREIGN_DRIVING_LICENCE_EXCHANGE', 'PL_MAZOWIECKIE_WARSAW',
     'Exactly which licence-issuing countries require a theoretical/practical exam (the non-convention branch) is a per-country fact needing its own research pass, not a single blanket rule.')
) AS v(procedure_code, jurisdiction_code, description)
JOIN procedures p ON p.code = v.procedure_code
JOIN jurisdictions j ON j.code = v.jurisdiction_code;

-- Real official sources cited by the catalogue for each of the 7 imported procedures -
-- every URL below is copied from PROCEDURE_CATALOGUE.md's own "MVP procedure source
-- records" section.
INSERT INTO official_sources (title, source_url, jurisdiction_id, language, source_type, last_checked_at, verification_status)
SELECT v.title, v.source_url, j.id, 'en', 'OFFICIAL_SERVICE_PAGE', '2026-09-01T00:00:00Z', 'DRAFT'
FROM (VALUES
    ('MOS - Temporary residence and work permit requirements',
     'https://mos.cudzoziemcy.gov.pl/en/informacje/czasowy-praca_VN/wymogi_EN', 'PL'),
    ('MOS - Temporary residence permit for studies',
     'https://www.mos.cudzoziemcy.gov.pl/en/informacje/studia-kurs_EN/wprowadzenie_EN', 'PL'),
    ('MOS - Family reunification (marriage to a Polish citizen)',
     'https://www.mos.cudzoziemcy.gov.pl/en/informacje/zwiazek-mal/wprowadzenie_EN', 'PL'),
    ('MSWiA - Registration of residence (EU/EEA/Swiss citizens)',
     'https://www.gov.pl/web/mswia-en/registration-of-residence', 'PL'),
    ('gov.pl - Obtaining a PESEL number, a service for foreigners',
     'https://www.gov.pl/web/gov/uzyskaj-numer-pesel--usluga-dla-cudzoziemcow-en', 'PL'),
    ('Warszawa 19115 - Assigning a PESEL number at the request of a foreigner',
     'https://warszawa19115.pl/en/-/assigning-a-pesel-number-at-the-request-of-a-foreigner-who-is-not-a-citizen-of-an-eu-efta-member-state-or-uk-country-or-a-member-of-their-family', 'PL_MAZOWIECKIE_WARSAW'),
    ('Warszawa 19115 - Temporary address registration (zameldowanie) for foreigners',
     'https://warszawa19115.pl/en/-/zameldowanie-na-pobyt-czasowy-cudzoziemcow-w-tym-obywateli-panstw-czlonkowskich-unii-europejskiej-ue-i-czlonkow-ich-rodzin', 'PL_MAZOWIECKIE_WARSAW'),
    ('Warszawa 19115 - Exchanging a convention-recognised foreign driving licence',
     'https://warszawa19115.pl/en/-/exchanging-a-foreign-driving-licence-issued-by-a-member-state-of-the-european-union-the-swiss-confederation-efta-a-state-party-to-the-convention-on-road-traffic-or-a-corresponding-model-driving-licence-as-defined-in-these-conventions-for-a-polish-driving-', 'PL_MAZOWIECKIE_WARSAW'),
    ('Warszawa 19115 - Exchanging a non-convention foreign driving licence',
     'https://warszawa19115.pl/en/-/exchange-of-a-foreign-driving-licence-not-specified-in-the-traffic-conventions-into-a-polish-driving-licence', 'PL_MAZOWIECKIE_WARSAW')
) AS v(title, source_url, jurisdiction_code)
JOIN jurisdictions j ON j.code = v.jurisdiction_code;

-- Link each imported DRAFT version to its primary source(s). PESEL and the driving
-- licence exchange each cite two real sources (brief §26's "a legal requirement may
-- have more than one source").
INSERT INTO procedure_version_sources (procedure_version_id, official_source_id, role)
SELECT pv.id, os.id, v.role
FROM (VALUES
    ('TEMP_RESIDENCE_WORK', 'MOS - Temporary residence and work permit requirements', 'PRIMARY'),
    ('TEMP_RESIDENCE_STUDY', 'MOS - Temporary residence permit for studies', 'PRIMARY'),
    ('FAMILY_REUNIFICATION', 'MOS - Family reunification (marriage to a Polish citizen)', 'PRIMARY'),
    ('EU_RESIDENCE_REGISTRATION', 'MSWiA - Registration of residence (EU/EEA/Swiss citizens)', 'PRIMARY'),
    ('PESEL', 'gov.pl - Obtaining a PESEL number, a service for foreigners', 'PRIMARY'),
    ('PESEL', 'Warszawa 19115 - Assigning a PESEL number at the request of a foreigner', 'SUPPORTING'),
    ('MELDUNEK', 'Warszawa 19115 - Temporary address registration (zameldowanie) for foreigners', 'PRIMARY'),
    ('FOREIGN_DRIVING_LICENCE_EXCHANGE', 'Warszawa 19115 - Exchanging a convention-recognised foreign driving licence', 'PRIMARY'),
    ('FOREIGN_DRIVING_LICENCE_EXCHANGE', 'Warszawa 19115 - Exchanging a non-convention foreign driving licence', 'SUPPORTING')
) AS v(procedure_code, source_title, role)
JOIN procedures p ON p.code = v.procedure_code
JOIN procedure_versions pv ON pv.procedure_id = p.id AND pv.version_number = 1
JOIN official_sources os ON os.title = v.source_title;
