-- GDPR-relevant consent trail (docs/database/DATABASE.md §1). Implemented now, not
-- deferred: registration already requires ToS/Privacy acceptance (brief §7), so a
-- provable, append-only consent record belongs alongside it from day one - waiting
-- until Phase 12 would mean early accounts have no consent record to backfill.
-- Marketing consent is deliberately never created by default (brief §4) - a row here
-- only ever exists because a user explicitly opted in.
CREATE TABLE user_consents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    consent_type    VARCHAR(30) NOT NULL
                        CHECK (consent_type IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'MARKETING_EMAILS')),
    policy_version  VARCHAR(50) NOT NULL,
    accepted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address      VARCHAR(45)
);

CREATE INDEX user_consents_user_idx ON user_consents (user_id);
