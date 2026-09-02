-- Assessment / AssessmentAnswer (docs/database/DATABASE.md §4, brief §23-§26).
-- Authenticated-only for Phase 5 (brief §31/§32 - anonymous/guest assessments are
-- explicitly deferred, see PHASE_5_REPORT.md "Deviations"): user_id is NOT NULL, unlike
-- the anonymous-session design DATABASE.md originally sketched.
CREATE TABLE assessments (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Permanently bound at creation time (brief §4/§32) - never re-resolved to a newer
    -- version while IN_PROGRESS, even if one publishes later.
    questionnaire_version_id   UUID NOT NULL REFERENCES questionnaire_versions (id) ON DELETE RESTRICT,
    -- Denormalized from questionnaire_version_id purely to make the "at most one
    -- IN_PROGRESS assessment per user per questionnaire identity" rule (brief §34) a
    -- single partial unique index below, without a subquery/trigger.
    questionnaire_id           UUID NOT NULL REFERENCES questionnaires (id) ON DELETE RESTRICT,
    status                     VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at               TIMESTAMPTZ,
    last_updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_version                BIGINT NOT NULL DEFAULT 0,
    CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'SUPERSEDED'))
);

CREATE INDEX assessments_user_status_idx ON assessments (user_id, status);

-- brief §34: "Allow at most one active IN_PROGRESS assessment per questionnaire
-- identity per user." Restarting (brief §35) or editing a completed assessment
-- (brief §36) must first transition the old row out of IN_PROGRESS.
CREATE UNIQUE INDEX assessments_one_in_progress_per_user_questionnaire_uq
    ON assessments (user_id, questionnaire_id) WHERE (status = 'IN_PROGRESS');

-- AssessmentAnswer - typed columns, not one untyped string/JSONB value (brief §24-§26's
-- explicit "GOOD" example: "Future Rules Engine must not parse arbitrary strings
-- constantly"). Exactly one of the *_value columns (or a row in
-- assessment_answer_options for MULTI_SELECT) is populated, matching the owning
-- Question.question_type - AssessmentAnswerService is the single writer that enforces
-- this; is_unsure=true means none of them are (brief §12 "I don't know" sentinel).
--
-- question_id references the stable Question identity, not questionnaire_question_id -
-- Phase 6 reads answers by stable Question.code across time (docs/database/DATABASE.md
-- §4), independent of which QuestionnaireVersion asked it.
CREATE TABLE assessment_answers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id     UUID NOT NULL REFERENCES assessments (id) ON DELETE CASCADE,
    question_id       UUID NOT NULL REFERENCES questions (id) ON DELETE RESTRICT,
    string_value      TEXT,
    boolean_value     BOOLEAN,
    integer_value     BIGINT,
    decimal_value     NUMERIC(14, 2),
    date_value        DATE,
    -- Single-select option code, or a COUNTRY/REGION/CITY/DISTRICT reference-data code.
    reference_code    VARCHAR(50),
    is_unsure         BOOLEAN NOT NULL DEFAULT FALSE,
    -- Recomputed by QuestionVisibilityService on every answer write (brief §28): false
    -- once this answer's question is no longer visible under the assessment's current
    -- answers. The row is kept (so re-showing the question restores the prior answer),
    -- but AssessmentFacts and completion validation only ever consider
    -- is_applicable = true rows (brief §28 "must include only currently applicable
    -- answers").
    is_applicable     BOOLEAN NOT NULL DEFAULT TRUE,
    answered_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX assessment_answers_assessment_question_uq
    ON assessment_answers (assessment_id, question_id);

-- MULTI_SELECT answers (e.g. GOALS) as a join table (brief §26's own "may be better
-- than JSONB for queryability" recommendation), not a comma-joined string or array
-- column.
CREATE TABLE assessment_answer_options (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_answer_id   UUID NOT NULL REFERENCES assessment_answers (id) ON DELETE CASCADE,
    option_code            VARCHAR(50) NOT NULL
);

CREATE UNIQUE INDEX assessment_answer_options_answer_code_uq
    ON assessment_answer_options (assessment_answer_id, option_code);
