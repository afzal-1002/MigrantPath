-- Only USER is used in Phase 2. Documented future codes (ADMIN, CONTENT_EDITOR,
-- LEGAL_REVIEWER, CONSULTANT, COMPANY_ADMIN - see docs/database/DATABASE.md §1) are
-- intentionally not seeded until a phase actually uses them.
INSERT INTO roles (code, name) VALUES ('USER', 'User');
