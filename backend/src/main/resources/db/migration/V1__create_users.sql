-- User identity table. Case-insensitive email uniqueness is enforced via a functional
-- unique index on lower(email) rather than the citext extension, to avoid an
-- extension dependency for something a plain expression index handles fine - see
-- docs/database/DATABASE.md §1.
--
-- Status lifecycle (docs/database/DATABASE.md §1 / brief §4):
--   PENDING_VERIFICATION -> ACTIVE (on email verification)
--   ACTIVE -> LOCKED (temporary, brute-force protection - see failed_login_attempts)
--   ACTIVE -> DISABLED (administrative action, not implemented in Phase 2)
--   any -> DELETED (soft-delete tombstone, per docs/database/DATABASE.md §0 - not
--   implemented until Phase 12's GDPR hardening; the status value is reserved now so
--   the CHECK constraint doesn't need a later migration to add it)
CREATE TABLE users (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                  VARCHAR(320) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    first_name             VARCHAR(100),
    preferred_language     VARCHAR(10),
    status                 VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                               CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DISABLED', 'DELETED')),
    email_verified         BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at      TIMESTAMPTZ,
    failed_login_attempts  INT NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    password_changed_at    TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));
