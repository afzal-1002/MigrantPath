-- Email verification and password reset tokens. Only a hash of the raw token is ever
-- stored (brief §34) - the raw token exists only in memory long enough to email it and
-- is never logged or persisted. token_hash is SHA-256 hex-encoded (64 chars); 128 is
-- given headroom without meaning anything more specific.
CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX email_verification_tokens_hash_uq ON email_verification_tokens (token_hash);
CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX password_reset_tokens_hash_uq ON password_reset_tokens (token_hash);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);
