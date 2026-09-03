-- Phase 10.5 (Production Rule Wiring): adds exactly one new PRIMARY_PURPOSE option,
-- GET_MELDUNEK, mirroring the existing GET_PESEL option exactly - both are "the user
-- explicitly told us this is what they want" product-relevance signals (see
-- docs/legal-content/PRODUCTION_RULE_COVERAGE.md and RECOMMENDATION_POLICY.md's own
-- "product policy vs legal rule" distinction), not a new legal fact.
--
-- Why a migration, not the Admin API, creates this DRAFT version: Phase 9's own
-- AdminQuestionnaireController deliberately does not expose question/option editing
-- (see that controller's class Javadoc - "a deliberate scope cut, documented in
-- PHASE_9_REPORT.md's Deviations"); only whole-version copy/submit/approve/publish/
-- archive exist through the real API. Question/QuestionOption rows are the same kind
-- of "stable structural identity" Procedure/Threshold identities were in V23/V34 - a
-- data-collection affordance, not itself a legal fact - so, consistent with that
-- precedent, the new DRAFT version's structure is seeded here, and it is then carried
-- through the real, governed DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED lifecycle
-- entirely through the Admin API at runtime (real actors, real AuditLog, real
-- self-approval block) - never published by this migration itself.
--
-- The clone below is a faithful, mechanical copy of QuestionnaireVersionService
-- .createDraftFrom's own algorithm (copy every QuestionnaireQuestion, then its
-- QuestionOptions, then - once every clone exists - its QuestionDependencies,
-- remapped to the new version's rows) expressed as SQL, so every one of v1's 18
-- questions, all their options, and all their dependency gates survive into v2
-- unchanged except for the one new option.

INSERT INTO questionnaire_versions (
    id, questionnaire_id, version_number, title, description, status
)
SELECT gen_random_uuid(), q.id, 2,
       'Warsaw General Eligibility Assessment - Version 2',
       'Adds one new goal option (Register my address / meldunek) so Meldunek can be recommended based on explicit user intent, the same way PESEL already is. No other question changes. DRAFT only - never published by this migration; see docs/legal-content/PRODUCTION_RULE_COVERAGE.md.',
       'DRAFT'
FROM questionnaires q WHERE q.code = 'WARSAW_GENERAL_ASSESSMENT';

-- Clone every questionnaire_questions row from v1 into v2, same question_id/section/
-- label/help_text/required/sort_order/option_source/allow_unsure/visibility_combinator.
INSERT INTO questionnaire_questions (
    id, questionnaire_version_id, question_id, section_code, label, help_text,
    required, sort_order, option_source, allow_unsure, visibility_combinator
)
SELECT gen_random_uuid(), v2.id, src.question_id, src.section_code, src.label, src.help_text,
       src.required, src.sort_order, src.option_source, src.allow_unsure, src.visibility_combinator
FROM questionnaire_questions src
JOIN questionnaire_versions v1
    ON v1.id = src.questionnaire_version_id
JOIN questionnaires quest ON quest.id = v1.questionnaire_id AND quest.code = 'WARSAW_GENERAL_ASSESSMENT'
    AND v1.version_number = 1
JOIN questionnaire_versions v2
    ON v2.questionnaire_id = v1.questionnaire_id AND v2.version_number = 2;

-- Clone every question_options row onto its v2 counterpart question.
INSERT INTO question_options (
    id, questionnaire_question_id, code, label, description, sort_order, active, reference_value
)
SELECT gen_random_uuid(), qq2.id, opt.code, opt.label, opt.description, opt.sort_order, opt.active, opt.reference_value
FROM question_options opt
JOIN questionnaire_questions qq1 ON qq1.id = opt.questionnaire_question_id
JOIN questionnaire_versions v1 ON v1.id = qq1.questionnaire_version_id
JOIN questionnaires quest ON quest.id = v1.questionnaire_id AND quest.code = 'WARSAW_GENERAL_ASSESSMENT'
    AND v1.version_number = 1
JOIN questionnaire_versions v2 ON v2.questionnaire_id = v1.questionnaire_id AND v2.version_number = 2
JOIN questionnaire_questions qq2 ON qq2.questionnaire_version_id = v2.id AND qq2.question_id = qq1.question_id;

-- The one new option: GET_MELDUNEK, appended after GET_PESEL (sort_order 85, between
-- GET_PESEL's 80 and UNSURE's 90) on v2's PRIMARY_PURPOSE question.
INSERT INTO question_options (id, questionnaire_question_id, code, label, description, sort_order, active)
SELECT gen_random_uuid(), qq2.id, 'GET_MELDUNEK', 'Register my address (meldunek)', NULL, 85, TRUE
FROM questionnaire_questions qq2
JOIN questionnaire_versions v2 ON v2.id = qq2.questionnaire_version_id
JOIN questionnaires quest ON quest.id = v2.questionnaire_id AND quest.code = 'WARSAW_GENERAL_ASSESSMENT'
    AND v2.version_number = 2
JOIN questions q ON q.id = qq2.question_id AND q.code = 'PRIMARY_PURPOSE';

-- Clone every question_dependencies row, remapped from v1's questionnaire_question ids
-- to v2's corresponding clones (both the gated question and the question it depends on).
INSERT INTO question_dependencies (
    id, questionnaire_question_id, depends_on_questionnaire_question_id, operator, expected_value
)
SELECT gen_random_uuid(), gated2.id, source2.id, dep.operator, dep.expected_value
FROM question_dependencies dep
JOIN questionnaire_questions gated1 ON gated1.id = dep.questionnaire_question_id
JOIN questionnaire_questions source1 ON source1.id = dep.depends_on_questionnaire_question_id
JOIN questionnaire_versions v1 ON v1.id = gated1.questionnaire_version_id
JOIN questionnaires quest ON quest.id = v1.questionnaire_id AND quest.code = 'WARSAW_GENERAL_ASSESSMENT'
    AND v1.version_number = 1
JOIN questionnaire_versions v2 ON v2.questionnaire_id = v1.questionnaire_id AND v2.version_number = 2
JOIN questionnaire_questions gated2 ON gated2.questionnaire_version_id = v2.id AND gated2.question_id = gated1.question_id
JOIN questionnaire_questions source2 ON source2.questionnaire_version_id = v2.id AND source2.question_id = source1.question_id;
