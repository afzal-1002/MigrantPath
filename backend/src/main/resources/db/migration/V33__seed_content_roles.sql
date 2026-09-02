-- New roles for the Phase 4 content-management API (brief §44-45). No user is granted
-- any of these here or anywhere else in a migration - granting one is an explicit,
-- separate administrative action, never a side effect of seeding the role itself
-- (mirrors V6's own USER-only seeding: adding a role is a data seed, granting it to a
-- specific account is a deliberate, auditable act). Existing USER-only registration
-- behavior (Phase 2) is completely unchanged.
INSERT INTO roles (code, name) VALUES
    ('CONTENT_EDITOR', 'Content Editor'),
    ('LEGAL_REVIEWER', 'Legal Reviewer'),
    ('ADMIN', 'Administrator');
