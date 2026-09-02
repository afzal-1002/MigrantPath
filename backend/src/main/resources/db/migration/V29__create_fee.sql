-- Fee (identity) / FeeVersion, snapshotted per ProcedureVersion - the brief (§20) asks
-- to choose between an independently-versioned Fee/FeeVersion and one tied completely
-- to ProcedureVersion; this follows DATABASE.md's original design, which is actually
-- the latter (FeeVersion.procedure_version_id was always present in the Phase 0
-- sketch). Kept that way deliberately: a fee's temporal validity is then identical to
-- its parent ProcedureVersion's (no separate exclusion constraint needed), and
-- snapshot-readiness (brief §49) falls out for free - "the fee that applied on date X"
-- is just "the FeeVersion belonging to the ProcedureVersion active on date X." A fee
-- changing independently of any other procedure content is rare enough in practice
-- (an amount bump usually accompanies a payment-instructions/context update anyway)
-- that a second independent temporal system isn't worth its complexity here.
CREATE TABLE fees (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    procedure_id   UUID NOT NULL REFERENCES procedures (id) ON DELETE RESTRICT,
    stable_code    VARCHAR(50) NOT NULL,
    fee_type       VARCHAR(30) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (fee_type IN ('APPLICATION', 'STAMP_DUTY', 'RESIDENCE_CARD', 'DOCUMENT_ISSUANCE', 'OTHER'))
);

CREATE UNIQUE INDEX fees_procedure_code_uq ON fees (procedure_id, stable_code);

CREATE TABLE fee_versions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_id                 UUID NOT NULL REFERENCES fees (id) ON DELETE RESTRICT,
    procedure_version_id   UUID NOT NULL REFERENCES procedure_versions (id) ON DELETE CASCADE,
    -- NUMERIC, never float/double, for money (brief §104).
    amount                 NUMERIC(10, 2) NOT NULL,
    -- VARCHAR, not CHAR (Phase 3's V7 already hit this exact mismatch): a Java String
    -- field maps to VARCHAR by default, not CHAR - Hibernate's schema validation fails
    -- otherwise ("found bpchar, but expecting varchar").
    currency               VARCHAR(3) NOT NULL,
    description            VARCHAR(500),
    payment_instructions   TEXT,
    refundable             BOOLEAN,
    CHECK (amount >= 0),
    CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX fee_versions_version_fee_uq ON fee_versions (procedure_version_id, fee_id);
