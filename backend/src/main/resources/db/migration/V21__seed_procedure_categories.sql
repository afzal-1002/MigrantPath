-- Full stable vocabulary seeded ahead of need (brief §5 lists all of these), same
-- "cheap, stable reference data" reasoning Phase 3 used to seed all 16 voivodeships
-- ahead of only Mazowieckie being operationally used - only 6 of these 11 are actually
-- used by the 8 MVP procedure identities seeded in V23; the rest are ready for Phase 10.
INSERT INTO procedure_categories (code, canonical_name, display_order) VALUES
    ('RESIDENCE', 'Residence', 10),
    ('EU_FREE_MOVEMENT', 'EU/EEA/Swiss free movement', 20),
    ('WORK', 'Work', 30),
    ('STUDY', 'Study', 40),
    ('FAMILY', 'Family', 50),
    ('LONG_TERM_RESIDENCE', 'Long-term residence', 60),
    ('PROTECTION', 'Protection', 70),
    ('IDENTITY_REGISTRATION', 'Identity & address registration', 80),
    ('DRIVING', 'Driving', 90),
    ('BUSINESS', 'Business', 100),
    ('OTHER', 'Other', 110);
