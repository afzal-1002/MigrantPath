-- Role/UserRole per docs/database/DATABASE.md §1. Only USER is seeded/used in Phase 2
-- (V6 seeds it) - ADMIN, CONTENT_EDITOR, LEGAL_REVIEWER, CONSULTANT, COMPANY_ADMIN are
-- documented future codes, not created as rows until a phase that uses them (no code
-- or schema change needed to add them later, just an INSERT).
CREATE TABLE roles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(50) NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL
);

CREATE TABLE user_roles (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);
