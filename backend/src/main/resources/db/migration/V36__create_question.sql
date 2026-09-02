-- Question (stable identity, docs/database/DATABASE.md §4) / QuestionnaireQuestion (the
-- per-version display+behavior configuration) / QuestionOption / QuestionDependency.
--
-- Question vs QuestionnaireQuestion split (IMPLEMENTATION_PLAN.md Phase 5, brief §6):
-- `Question` is the stable, rule-facing identity a Phase 6 RuleCondition and an
-- AssessmentAnswer both reference by `code`/`field_key` and that must never be renamed
-- once in use (brief §43 - "treat them as API/domain contracts"). `QuestionnaireQuestion`
-- is where a specific QuestionnaireVersion configures how that question is presented and
-- gated - label, section, required-ness, branching - so the same semantic question could
-- in principle be reused (or reworded, re-sectioned, re-gated) across versions without
-- ever changing its stable code.
CREATE TABLE questions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(80) NOT NULL,
    -- The camelCase name a future RuleCondition.field / AssessmentFacts map key uses -
    -- decoupled from `code` so a human-facing rename never touches rule definitions
    -- (docs/database/DATABASE.md §4).
    field_key           VARCHAR(80) NOT NULL,
    question_type       VARCHAR(20) NOT NULL,
    -- Distinguishes UI widget from semantic meaning (brief §8) - nullable: most
    -- questions have no special semantic meaning beyond their question_type.
    semantic_data_type  VARCHAR(20),
    unit                VARCHAR(30),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (question_type IN (
        'BOOLEAN', 'SINGLE_SELECT', 'MULTI_SELECT', 'TEXT', 'INTEGER', 'DECIMAL',
        'DATE', 'COUNTRY', 'REGION', 'CITY', 'DISTRICT'
    )),
    CHECK (semantic_data_type IS NULL OR semantic_data_type IN ('GENERIC', 'MONEY'))
);

CREATE UNIQUE INDEX questions_code_uq ON questions (code);
CREATE UNIQUE INDEX questions_field_key_uq ON questions (field_key);

CREATE TABLE questionnaire_questions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    questionnaire_version_id UUID NOT NULL REFERENCES questionnaire_versions (id) ON DELETE CASCADE,
    question_id             UUID NOT NULL REFERENCES questions (id) ON DELETE RESTRICT,
    section_code            VARCHAR(50) NOT NULL,
    label                    VARCHAR(500) NOT NULL,
    help_text                VARCHAR(1000),
    required                 BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order               INT NOT NULL,
    -- STATIC = options come from question_options below; REFERENCE_* = options come
    -- from Phase 3 reference data (brief §10/§11) - never duplicated into
    -- question_options.
    option_source            VARCHAR(20) NOT NULL DEFAULT 'STATIC',
    allow_unsure              BOOLEAN NOT NULL DEFAULT FALSE,
    -- How this question's QuestionDependency rows combine when more than one exists
    -- (brief §70) - ALL (AND) is the sane default; ANY (OR) is opt-in per question.
    visibility_combinator     VARCHAR(5) NOT NULL DEFAULT 'ALL',
    CHECK (option_source IN ('STATIC', 'REFERENCE_COUNTRY', 'REFERENCE_REGION', 'REFERENCE_CITY', 'REFERENCE_DISTRICT')),
    CHECK (visibility_combinator IN ('ALL', 'ANY'))
);

CREATE UNIQUE INDEX questionnaire_questions_version_question_uq
    ON questionnaire_questions (questionnaire_version_id, question_id);
CREATE INDEX questionnaire_questions_version_section_idx
    ON questionnaire_questions (questionnaire_version_id, section_code, sort_order);

CREATE TABLE question_options (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    questionnaire_question_id UUID NOT NULL REFERENCES questionnaire_questions (id) ON DELETE CASCADE,
    code                     VARCHAR(50) NOT NULL,
    label                    VARCHAR(300) NOT NULL,
    description              VARCHAR(500),
    sort_order                INT NOT NULL,
    active                    BOOLEAN NOT NULL DEFAULT TRUE,
    -- Free-form escape hatch for an option that itself maps to a further reference-data
    -- value (unused by the seeded MVP set; reserved for a later option that needs it).
    reference_value           VARCHAR(50)
);

CREATE UNIQUE INDEX question_options_question_code_uq
    ON question_options (questionnaire_question_id, code);

-- QuestionDependency (brief §13/§14) - "should this question be shown," evaluated by
-- the shared com.foreignerwarsaw.common.evaluation.ConditionEvaluator (the same
-- operator vocabulary Phase 6's RuleCondition will reuse, per
-- IMPLEMENTATION_PLAN.md 5.2 - never a second, incompatible evaluator). Deliberately
-- NOT the immigration Rules Engine.
CREATE TABLE question_dependencies (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    questionnaire_question_id   UUID NOT NULL REFERENCES questionnaire_questions (id) ON DELETE CASCADE,
    depends_on_questionnaire_question_id UUID NOT NULL REFERENCES questionnaire_questions (id) ON DELETE CASCADE,
    operator                    VARCHAR(25) NOT NULL,
    -- JSONB holds either a scalar (string/boolean/number) or an array (for IN/NOT_IN/
    -- CONTAINS/NOT_CONTAINS) - one column, evaluated generically, deliberately the same
    -- "typed JSON value, not a stringified literal" shape Phase 6's RuleCondition tree
    -- will use (docs/database/DATABASE.md §5) so the two stay behaviorally compatible.
    expected_value               JSONB NOT NULL,
    CHECK (operator IN (
        'EQUALS', 'NOT_EQUALS', 'IN', 'NOT_IN', 'CONTAINS', 'NOT_CONTAINS',
        'EXISTS', 'NOT_EXISTS', 'GREATER_THAN', 'GREATER_THAN_OR_EQUAL',
        'LESS_THAN', 'LESS_THAN_OR_EQUAL', 'DATE_BEFORE', 'DATE_AFTER'
    )),
    CHECK (questionnaire_question_id <> depends_on_questionnaire_question_id)
);

CREATE INDEX question_dependencies_gated_idx ON question_dependencies (questionnaire_question_id);
CREATE INDEX question_dependencies_source_idx ON question_dependencies (depends_on_questionnaire_question_id);
