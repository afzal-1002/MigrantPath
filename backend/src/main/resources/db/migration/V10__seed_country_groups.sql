-- See ADR-006 for why THIRD_COUNTRY and UK_WITHDRAWAL_AGREEMENT are deliberately
-- absent from this list.
INSERT INTO country_groups (code, name, description, group_type) VALUES
    ('EU_MEMBER', 'European Union member state', 'Current and former (time-bounded) EU member states.', 'LEGAL'),
    ('EEA', 'European Economic Area', 'EU member states plus Iceland, Liechtenstein and Norway.', 'LEGAL'),
    ('EFTA', 'European Free Trade Association', 'Iceland, Liechtenstein, Norway and Switzerland.', 'LEGAL'),
    ('SCHENGEN', 'Schengen Area', 'States that have abolished internal border controls under the Schengen acquis.', 'LEGAL'),
    ('EU_EEA_SWISS', 'EU, EEA or Switzerland (convenience aggregate)', 'Application-defined convenience grouping (EU_MEMBER + EEA-only additions + Switzerland) used where Polish administrative practice commonly treats these together (e.g. driving licence recognition) - not itself a distinct EU-law category. See ADR-006.', 'CONVENIENCE');
