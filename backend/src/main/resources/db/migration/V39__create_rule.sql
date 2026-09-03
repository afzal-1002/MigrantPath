-- Rule (identity) / RuleVersion (docs/database/DATABASE.md §5, ADR-009). Same
-- identity+version+publication-lifecycle+exclusion-constraint pattern as
-- Procedure/ProcedureVersion (V22/V25) and Threshold/ThresholdVersion (V30) - reuses the
-- exact same PublicationStatus vocabulary, never a duplicated one.
--
-- condition_tree is JSONB, not normalized condition rows (DATABASE.md §5's own reasoned
-- decision: a recursive ALL/ANY/NOT tree is read and evaluated as a whole by the Java
-- evaluator - nothing ever needs SQL to filter *inside* one rule's logic). Validated by
-- application-level JSON Schema on write (brief §66/§67), not by the database.
CREATE TABLE rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL,
    canonical_name  VARCHAR(200) NOT NULL,
    -- What kind of legal question this rule answers (brief §7) - never conflated with
    -- Phase 7's match_type (PRIMARY_MATCH/...), which is a *ranking* of the outcome,
    -- not the rule's own purpose.
    rule_type       VARCHAR(30) NOT NULL,
    -- What this rule evaluates eligibility/applicability *for* (brief §6). PROCEDURE is
    -- the only target this phase actually exercises; the column stays generic so a
    -- later DOCUMENT_REQUIREMENT/STEP/FEE target needs no schema change.
    target_type     VARCHAR(30) NOT NULL,
    -- The stable business code of the target (e.g. a Procedure.code) - never a version
    -- id (brief §61/§62: Rule and target content version independently; evaluationDate
    -- ties them together at read time, not a stored pairing). No DB FK here since
    -- target_type varies by row; RulePublishingService validates the target exists.
    target_code     VARCHAR(50) NOT NULL,
    jurisdiction_id UUID REFERENCES jurisdictions (id) ON DELETE RESTRICT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (rule_type IN ('ELIGIBILITY', 'APPLICABILITY', 'EXCLUSION', 'REQUIREMENT', 'INFORMATION_REQUIRED')),
    CHECK (target_type IN ('PROCEDURE', 'DOCUMENT_REQUIREMENT', 'STEP', 'FEE', 'THRESHOLD_APPLICABILITY', 'ROUTING'))
);

CREATE UNIQUE INDEX rules_code_uq ON rules (code);
CREATE INDEX rules_target_idx ON rules (target_type, target_code);

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE rule_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id              UUID NOT NULL REFERENCES rules (id) ON DELETE RESTRICT,
    version_number       INT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_from       DATE,
    effective_to         DATE,
    condition_tree       JSONB NOT NULL,
    -- Schema-evolution escape hatch (brief §67) - bumped only if the condition JSON
    -- shape itself ever needs a breaking change; irrelevant to ordinary rule content
    -- changes, which are just a new RuleVersion.
    condition_schema_version INT NOT NULL DEFAULT 1,
    -- A stable key (never prose) a future Phase 7 translates into user-facing text
    -- (brief §34/§83) - e.g. "rule.blueCard.base". Nullable: not every rule needs a
    -- whole-rule explanation beyond its per-condition explanationKeys (see V40).
    explanation_key      VARCHAR(200),
    change_summary       VARCHAR(1000),
    created_by           UUID REFERENCES users (id) ON DELETE SET NULL,
    submitted_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    approved_by          UUID REFERENCES users (id) ON DELETE SET NULL,
    published_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    submitted_at         TIMESTAMPTZ,
    approved_at          TIMESTAMPTZ,
    published_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_version         BIGINT NOT NULL DEFAULT 0,
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED')),
    CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX rule_versions_rule_version_number_uq ON rule_versions (rule_id, version_number);
CREATE INDEX rule_versions_rule_status_idx ON rule_versions (rule_id, status);
CREATE INDEX rule_versions_effective_idx ON rule_versions (effective_from, effective_to);

-- At most one PUBLISHED version of the same Rule may have an active date range at any
-- given moment - identical convention to procedure_versions (V25) and
-- threshold_versions (V30).
ALTER TABLE rule_versions
    ADD CONSTRAINT rule_versions_no_overlapping_published
    EXCLUDE USING gist (
        rule_id WITH =,
        daterange(effective_from, effective_to) WITH &&
    ) WHERE (status = 'PUBLISHED');

-- Rule -> OfficialSource provenance (brief §22/§57, docs/database/DATABASE.md §7) - same
-- shape as procedure_version_sources/threshold_version_sources (V31).
CREATE TABLE rule_version_sources (
    rule_version_id      UUID NOT NULL REFERENCES rule_versions (id) ON DELETE CASCADE,
    official_source_id   UUID NOT NULL REFERENCES official_sources (id) ON DELETE RESTRICT,
    role                  VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    PRIMARY KEY (rule_version_id, official_source_id),
    CHECK (role IN ('PRIMARY', 'SUPPORTING', 'LEGAL_BASIS', 'OPERATIONAL'))
);
