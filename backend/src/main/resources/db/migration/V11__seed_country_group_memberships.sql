-- Current membership is seeded with high confidence; historical accession dates
-- before roughly 2000 are approximate (compiled from general knowledge of EU/EEA/
-- EFTA/Schengen enlargement history, not a single authoritative dataset) - see
-- docs/reference/REFERENCE_DATA_SOURCES.md, which tracks this as DRAFT pending a
-- dedicated verification pass, consistent with how docs/product/PROCEDURE_CATALOGUE.md
-- handles the same DRAFT/VERIFIED distinction for legal content. What matters most for
-- the application today - which countries are members *right now* - is accurate;
-- pre-2000 date precision is the part flagged for review.
--
-- The (country_code, group_code, valid_from, valid_to) VALUES rows are joined against
-- already-seeded countries/country_groups by their stable codes (V8/V10) rather than
-- hand-copied UUIDs - self-documenting and immune to seeding order.
INSERT INTO country_group_memberships (country_id, country_group_id, valid_from, valid_to)
SELECT c.id, g.id, v.valid_from::date, v.valid_to::date
FROM (VALUES
    -- ===== EU_MEMBER (27 current + United Kingdom historical) =====
    ('BE','EU_MEMBER','1958-01-01',NULL), ('DE','EU_MEMBER','1958-01-01',NULL),
    ('FR','EU_MEMBER','1958-01-01',NULL), ('IT','EU_MEMBER','1958-01-01',NULL),
    ('LU','EU_MEMBER','1958-01-01',NULL), ('NL','EU_MEMBER','1958-01-01',NULL),
    ('DK','EU_MEMBER','1973-01-01',NULL), ('IE','EU_MEMBER','1973-01-01',NULL),
    ('GB','EU_MEMBER','1973-01-01','2020-01-31'),
    ('GR','EU_MEMBER','1981-01-01',NULL),
    ('ES','EU_MEMBER','1986-01-01',NULL), ('PT','EU_MEMBER','1986-01-01',NULL),
    ('AT','EU_MEMBER','1995-01-01',NULL), ('FI','EU_MEMBER','1995-01-01',NULL), ('SE','EU_MEMBER','1995-01-01',NULL),
    ('CY','EU_MEMBER','2004-05-01',NULL), ('CZ','EU_MEMBER','2004-05-01',NULL), ('EE','EU_MEMBER','2004-05-01',NULL),
    ('HU','EU_MEMBER','2004-05-01',NULL), ('LV','EU_MEMBER','2004-05-01',NULL), ('LT','EU_MEMBER','2004-05-01',NULL),
    ('MT','EU_MEMBER','2004-05-01',NULL), ('PL','EU_MEMBER','2004-05-01',NULL), ('SK','EU_MEMBER','2004-05-01',NULL),
    ('SI','EU_MEMBER','2004-05-01',NULL),
    ('BG','EU_MEMBER','2007-01-01',NULL), ('RO','EU_MEMBER','2007-01-01',NULL),
    ('HR','EU_MEMBER','2013-07-01',NULL),

    -- ===== EEA (EU members' EEA-equivalent participation + Iceland/Liechtenstein/Norway) =====
    ('BE','EEA','1994-01-01',NULL), ('DE','EEA','1994-01-01',NULL), ('FR','EEA','1994-01-01',NULL),
    ('IT','EEA','1994-01-01',NULL), ('LU','EEA','1994-01-01',NULL), ('NL','EEA','1994-01-01',NULL),
    ('DK','EEA','1994-01-01',NULL), ('IE','EEA','1994-01-01',NULL), ('GB','EEA','1994-01-01','2020-01-31'),
    ('GR','EEA','1994-01-01',NULL), ('ES','EEA','1994-01-01',NULL), ('PT','EEA','1994-01-01',NULL),
    ('AT','EEA','1995-01-01',NULL), ('FI','EEA','1995-01-01',NULL), ('SE','EEA','1995-01-01',NULL),
    ('CY','EEA','2004-05-01',NULL), ('CZ','EEA','2004-05-01',NULL), ('EE','EEA','2004-05-01',NULL),
    ('HU','EEA','2004-05-01',NULL), ('LV','EEA','2004-05-01',NULL), ('LT','EEA','2004-05-01',NULL),
    ('MT','EEA','2004-05-01',NULL), ('PL','EEA','2004-05-01',NULL), ('SK','EEA','2004-05-01',NULL),
    ('SI','EEA','2004-05-01',NULL),
    ('BG','EEA','2007-01-01',NULL), ('RO','EEA','2007-01-01',NULL),
    ('HR','EEA','2013-07-01',NULL),
    ('IS','EEA','1994-01-01',NULL), ('NO','EEA','1994-01-01',NULL), ('LI','EEA','1995-05-01',NULL),

    -- ===== EFTA (current membership - Iceland, Liechtenstein, Norway, Switzerland) =====
    ('IS','EFTA','1995-01-01',NULL), ('NO','EFTA','1995-01-01',NULL),
    ('CH','EFTA','1995-01-01',NULL), ('LI','EFTA','1991-09-01',NULL),

    -- ===== SCHENGEN (29 members; Ireland, Cyprus, and the UK have never joined) =====
    ('BE','SCHENGEN','1995-03-26',NULL), ('FR','SCHENGEN','1995-03-26',NULL), ('DE','SCHENGEN','1995-03-26',NULL),
    ('LU','SCHENGEN','1995-03-26',NULL), ('NL','SCHENGEN','1995-03-26',NULL), ('PT','SCHENGEN','1995-03-26',NULL),
    ('ES','SCHENGEN','1995-03-26',NULL),
    ('AT','SCHENGEN','1997-12-01',NULL), ('IT','SCHENGEN','1997-12-01',NULL),
    ('GR','SCHENGEN','2000-03-26',NULL),
    ('DK','SCHENGEN','2001-03-25',NULL), ('FI','SCHENGEN','2001-03-25',NULL), ('SE','SCHENGEN','2001-03-25',NULL),
    ('IS','SCHENGEN','2001-03-25',NULL), ('NO','SCHENGEN','2001-03-25',NULL),
    ('CZ','SCHENGEN','2007-12-21',NULL), ('EE','SCHENGEN','2007-12-21',NULL), ('HU','SCHENGEN','2007-12-21',NULL),
    ('LV','SCHENGEN','2007-12-21',NULL), ('LT','SCHENGEN','2007-12-21',NULL), ('MT','SCHENGEN','2007-12-21',NULL),
    ('PL','SCHENGEN','2007-12-21',NULL), ('SK','SCHENGEN','2007-12-21',NULL), ('SI','SCHENGEN','2007-12-21',NULL),
    ('CH','SCHENGEN','2008-12-12',NULL),
    ('LI','SCHENGEN','2011-12-19',NULL),
    ('HR','SCHENGEN','2023-01-01',NULL),
    ('BG','SCHENGEN','2025-01-01',NULL), ('RO','SCHENGEN','2025-01-01',NULL),

    -- ===== EU_EEA_SWISS (convenience aggregate - see V10) =====
    ('BE','EU_EEA_SWISS','1958-01-01',NULL), ('DE','EU_EEA_SWISS','1958-01-01',NULL), ('FR','EU_EEA_SWISS','1958-01-01',NULL),
    ('IT','EU_EEA_SWISS','1958-01-01',NULL), ('LU','EU_EEA_SWISS','1958-01-01',NULL), ('NL','EU_EEA_SWISS','1958-01-01',NULL),
    ('DK','EU_EEA_SWISS','1973-01-01',NULL), ('IE','EU_EEA_SWISS','1973-01-01',NULL),
    ('GR','EU_EEA_SWISS','1981-01-01',NULL),
    ('ES','EU_EEA_SWISS','1986-01-01',NULL), ('PT','EU_EEA_SWISS','1986-01-01',NULL),
    ('AT','EU_EEA_SWISS','1995-01-01',NULL), ('FI','EU_EEA_SWISS','1995-01-01',NULL), ('SE','EU_EEA_SWISS','1995-01-01',NULL),
    ('CY','EU_EEA_SWISS','2004-05-01',NULL), ('CZ','EU_EEA_SWISS','2004-05-01',NULL), ('EE','EU_EEA_SWISS','2004-05-01',NULL),
    ('HU','EU_EEA_SWISS','2004-05-01',NULL), ('LV','EU_EEA_SWISS','2004-05-01',NULL), ('LT','EU_EEA_SWISS','2004-05-01',NULL),
    ('MT','EU_EEA_SWISS','2004-05-01',NULL), ('PL','EU_EEA_SWISS','2004-05-01',NULL), ('SK','EU_EEA_SWISS','2004-05-01',NULL),
    ('SI','EU_EEA_SWISS','2004-05-01',NULL),
    ('BG','EU_EEA_SWISS','2007-01-01',NULL), ('RO','EU_EEA_SWISS','2007-01-01',NULL),
    ('HR','EU_EEA_SWISS','2013-07-01',NULL),
    ('IS','EU_EEA_SWISS','1994-01-01',NULL), ('NO','EU_EEA_SWISS','1994-01-01',NULL), ('LI','EU_EEA_SWISS','1995-05-01',NULL),
    -- Switzerland: not EU/EEA, included via the 1999 EU-Swiss bilateral agreement on
    -- free movement of persons (in force from 2002-06-01), which is the actual reason
    -- Polish administrative practice often treats Swiss nationals equivalently.
    ('CH','EU_EEA_SWISS','2002-06-01',NULL)
) AS v(country_code, group_code, valid_from, valid_to)
JOIN countries c ON c.code = v.country_code
JOIN country_groups g ON g.code = v.group_code;
