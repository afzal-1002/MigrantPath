-- Phase 9: administrative content-governance tables (brief §60/§66/§109) plus one
-- structural fix carried along in the same migration rather than a separate one-line
-- migration: threshold_versions never gained a submitted_by column when it was first
-- created (V30) - every sibling versioned table (procedure_versions, rule_versions,
-- questionnaire_versions) always had one. Phase 9's review workflow needs it uniformly
-- across all four content types (self-approval prevention compares the reviewer against
-- whoever submitted, not just whoever originally drafted).
ALTER TABLE threshold_versions ADD COLUMN submitted_by UUID REFERENCES users (id) ON DELETE SET NULL;

-- Append-only administrative audit trail (brief §60-§64). Never updated/deleted by
-- application code - see AuditLog's own Javadoc. entity_id/entity_version_id are plain
-- UUID columns without a foreign key: a single audit table spans many different entity
-- tables (procedures, rules, thresholds, sources, questionnaires, users...), and an
-- audit row for a since-deleted row must remain readable, so a hard FK here would be
-- actively wrong, not just unnecessary.
CREATE TABLE audit_log (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    action_type           VARCHAR(60) NOT NULL,
    entity_type           VARCHAR(40) NOT NULL,
    entity_id             UUID,
    entity_business_code  VARCHAR(50),
    entity_version_id     UUID,
    occurred_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    summary               VARCHAR(500) NOT NULL,
    metadata              JSONB
);

CREATE INDEX audit_log_actor_occurred_idx ON audit_log (actor_user_id, occurred_at DESC);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id);
CREATE INDEX audit_log_action_occurred_idx ON audit_log (action_type, occurred_at DESC);

-- One active review per (entity_type, entity_version_id) at a time (brief §112) -
-- reviewer decisions and self-approval prevention both key off this row rather than
-- re-deriving "who submitted this" from four different version tables (brief §6's
-- explicit invitation to extract shared lifecycle bookkeeping, while each entity's own
-- publish-readiness validation stays domain-specific and untouched).
CREATE TABLE admin_review (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type        VARCHAR(40) NOT NULL,
    entity_version_id  UUID NOT NULL,
    submitted_by       UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    reviewer           UUID REFERENCES users (id) ON DELETE SET NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment            VARCHAR(2000),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    CHECK (status IN ('PENDING', 'APPROVED', 'CHANGES_REQUESTED', 'REJECTED'))
);

-- Only one PENDING review may exist per version at a time - a second submit while one
-- is already open is rejected rather than silently opening a duplicate (brief §112).
CREATE UNIQUE INDEX admin_review_one_pending_per_version_uq
    ON admin_review (entity_type, entity_version_id)
    WHERE (status = 'PENDING');

CREATE INDEX admin_review_entity_idx ON admin_review (entity_type, entity_version_id);
CREATE INDEX admin_review_status_idx ON admin_review (status, created_at DESC);
