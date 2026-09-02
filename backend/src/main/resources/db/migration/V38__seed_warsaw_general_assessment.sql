-- Seeds the MVP "Help me choose" questionnaire (brief §41/§42/§87): WARSAW_GENERAL_ASSESSMENT,
-- Version 1, PUBLISHED immediately - this is factual-data collection only, no legal
-- eligibility content, so it does not need the DRAFT-review caution V34's procedure
-- content seed used (brief §41: "acceptable to publish a limited questionnaire that
-- only collects factual information").
--
-- 18 questions across every wizard section (About You, Current Status, Your Goal, Work,
-- Study, Family, Long-Term) - a curated subset of ASSESSMENT_DECISION_TREE.md's full
-- ~80-code brainstorm, not all of it (brief §42/§87: "Quality > quantity" / "Do NOT
-- immediately implement 80 questions"). See docs/product/QUESTION_CODES.md for the
-- full registry and, for each question, why it's needed.
INSERT INTO questionnaires (code, canonical_name, active)
VALUES ('WARSAW_GENERAL_ASSESSMENT', 'Warsaw General Eligibility Assessment', TRUE);

INSERT INTO questionnaire_versions (
    questionnaire_id, version_number, title, description, status, effective_from, published_at
)
SELECT q.id, 1, 'Warsaw General Eligibility Assessment - Version 1',
       'Collects the facts needed to later evaluate which Warsaw immigration/admin procedures may apply. Does not itself determine eligibility.',
       'PUBLISHED', DATE '2026-01-01', TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM questionnaires q WHERE q.code = 'WARSAW_GENERAL_ASSESSMENT';

-- --- Question identities ---

INSERT INTO questions (code, field_key, question_type, semantic_data_type, unit)
VALUES
    ('CITIZENSHIP_COUNTRY',        'citizenshipCountry',        'COUNTRY',       'GENERIC', NULL),
    ('CURRENTLY_IN_POLAND',        'currentlyInPoland',         'BOOLEAN',       'GENERIC', NULL),
    ('CURRENT_COUNTRY',            'currentCountry',            'COUNTRY',       'GENERIC', NULL),
    ('DATE_OF_BIRTH',              'dateOfBirth',                'DATE',          'GENERIC', NULL),
    ('CURRENT_LEGAL_STATUS',       'currentLegalStatus',         'SINGLE_SELECT', 'GENERIC', NULL),
    ('CURRENT_STATUS_EXPIRY_DATE', 'currentStatusExpiryDate',    'DATE',          'GENERIC', NULL),
    ('PRIMARY_PURPOSE',            'purposes',                   'MULTI_SELECT',  'GENERIC', NULL),
    ('HAS_JOB_OFFER',              'hasJobOffer',                'BOOLEAN',       'GENERIC', NULL),
    ('EMPLOYMENT_CONTRACT_TYPE',   'employmentContractType',     'SINGLE_SELECT', 'GENERIC', NULL),
    ('MONTHLY_GROSS_SALARY',       'monthlyGrossSalary',         'DECIMAL',       'MONEY',   'PLN_MONTHLY_GROSS'),
    ('HIGHLY_QUALIFIED',           'highlyQualified',            'BOOLEAN',       'GENERIC', NULL),
    ('CURRENTLY_STUDYING',         'currentlyStudying',          'BOOLEAN',       'GENERIC', NULL),
    ('STUDY_MODE',                 'studyMode',                  'SINGLE_SELECT', 'GENERIC', NULL),
    ('EXPECTED_GRADUATION_DATE',   'expectedGraduationDate',     'DATE',          'GENERIC', NULL),
    ('MARITAL_STATUS',             'maritalStatus',              'SINGLE_SELECT', 'GENERIC', NULL),
    ('SPOUSE_CITIZENSHIP',         'spouseCitizenship',          'COUNTRY',       'GENERIC', NULL),
    ('YEARS_IN_POLAND',            'yearsInPoland',              'INTEGER',       'GENERIC', NULL),
    ('HAS_KARTA_POLAKA',           'hasKartaPolaka',              'BOOLEAN',       'GENERIC', NULL);

-- --- QuestionnaireQuestion (per-version presentation + gating configuration) ---

INSERT INTO questionnaire_questions (
    questionnaire_version_id, question_id, section_code, label, help_text, required,
    sort_order, option_source, allow_unsure, visibility_combinator
)
SELECT qv.id, q.id, v.section_code, v.label, v.help_text, v.required, v.sort_order,
       v.option_source, v.allow_unsure, v.visibility_combinator
FROM (VALUES
    ('CITIZENSHIP_COUNTRY', 'ABOUT_YOU', 'Which country are you a citizen of?',
     'If you hold more than one citizenship, choose the one most relevant to your move to Poland.',
     TRUE, 10, 'REFERENCE_COUNTRY', FALSE, 'ALL'),
    ('CURRENTLY_IN_POLAND', 'ABOUT_YOU', 'Are you currently in Poland?',
     'This affects which procedures and offices are relevant to you.', TRUE, 20, 'STATIC', FALSE, 'ALL'),
    ('CURRENT_COUNTRY', 'ABOUT_YOU', 'Which country are you currently in?',
     'Helps us understand your starting point if you are applying from abroad.', FALSE, 30, 'REFERENCE_COUNTRY', FALSE, 'ALL'),
    ('DATE_OF_BIRTH', 'ABOUT_YOU', 'What is your date of birth?',
     'Some procedures have age-related requirements.', TRUE, 40, 'STATIC', FALSE, 'ALL'),
    ('CURRENT_LEGAL_STATUS', 'CURRENT_STATUS', 'What is your current legal status in Poland?',
     'Choose the option that best matches your current situation. Not sure is a valid answer.', TRUE, 50, 'STATIC', FALSE, 'ALL'),
    ('CURRENT_STATUS_EXPIRY_DATE', 'CURRENT_STATUS', 'When does your current status expire?',
     'Check the expiry date on your document if you have one.', FALSE, 60, 'STATIC', TRUE, 'ALL'),
    ('PRIMARY_PURPOSE', 'YOUR_GOAL', 'What would you like to do?',
     'Select everything that applies - you can choose more than one.', TRUE, 70, 'STATIC', FALSE, 'ALL'),
    ('HAS_JOB_OFFER', 'WORK', 'Do you have a job offer in Poland?',
     NULL, TRUE, 80, 'STATIC', FALSE, 'ANY'),
    ('EMPLOYMENT_CONTRACT_TYPE', 'WORK', 'What type of contract does the job offer involve?',
     'Choose Not sure if you have not seen the contract terms yet.', FALSE, 90, 'STATIC', FALSE, 'ALL'),
    ('MONTHLY_GROSS_SALARY', 'WORK', 'What is the monthly gross salary offered (in PLN)?',
     'Gross salary before tax, as stated in the job offer.', FALSE, 100, 'STATIC', TRUE, 'ALL'),
    ('HIGHLY_QUALIFIED', 'WORK', 'Is this a highly qualified position (e.g. requiring a university degree)?',
     NULL, FALSE, 110, 'STATIC', FALSE, 'ALL'),
    ('CURRENTLY_STUDYING', 'STUDY', 'Are you currently enrolled as a student?',
     NULL, TRUE, 120, 'STATIC', FALSE, 'ALL'),
    ('STUDY_MODE', 'STUDY', 'Is your programme full-time or part-time?',
     NULL, FALSE, 130, 'STATIC', FALSE, 'ALL'),
    ('EXPECTED_GRADUATION_DATE', 'STUDY', 'When do you expect to graduate?',
     'An approximate date is fine.', FALSE, 140, 'STATIC', TRUE, 'ALL'),
    ('MARITAL_STATUS', 'FAMILY', 'What is your marital status?',
     NULL, TRUE, 150, 'STATIC', FALSE, 'ANY'),
    ('SPOUSE_CITIZENSHIP', 'FAMILY', 'Which country is your spouse a citizen of?',
     NULL, FALSE, 160, 'REFERENCE_COUNTRY', FALSE, 'ALL'),
    ('YEARS_IN_POLAND', 'LONG_TERM', 'How many years have you lived in Poland in total?',
     'An approximate whole number of years is fine.', TRUE, 170, 'STATIC', FALSE, 'ANY'),
    ('HAS_KARTA_POLAKA', 'LONG_TERM', 'Do you hold a Karta Polaka (Pole''s Card)?',
     NULL, FALSE, 180, 'STATIC', FALSE, 'ANY')
) AS v(question_code, section_code, label, help_text, required, sort_order, option_source, allow_unsure, visibility_combinator)
JOIN questions q ON q.code = v.question_code
CROSS JOIN (
    SELECT qv.id FROM questionnaire_versions qv
    JOIN questionnaires quest ON quest.id = qv.questionnaire_id
    WHERE quest.code = 'WARSAW_GENERAL_ASSESSMENT' AND qv.version_number = 1
) AS qv;

-- --- Static QuestionOption rows (STATIC option_source questions only - reference-backed
-- questions like CITIZENSHIP_COUNTRY get their options from Phase 3 reference data,
-- brief §10/§11) ---

INSERT INTO question_options (questionnaire_question_id, code, label, sort_order, active)
SELECT qq.id, v.option_code, v.label, v.sort_order, TRUE
FROM (VALUES
    -- CURRENTLY_IN_POLAND (BOOLEAN questions render Yes/No natively in the UI - no
    -- QuestionOption rows needed; included here only for questions that are actually
    -- SINGLE_SELECT/MULTI_SELECT).

    -- CURRENT_LEGAL_STATUS (brief §9's exact option list)
    ('CURRENT_LEGAL_STATUS', 'NONE', 'No legal status / just arrived', 10),
    ('CURRENT_LEGAL_STATUS', 'VISA_FREE', 'Visa-free stay', 20),
    ('CURRENT_LEGAL_STATUS', 'SCHENGEN_VISA', 'Schengen (C) visa', 30),
    ('CURRENT_LEGAL_STATUS', 'POLISH_NATIONAL_VISA', 'Polish national (D) visa', 40),
    ('CURRENT_LEGAL_STATUS', 'TEMPORARY_RESIDENCE_PERMIT', 'Temporary residence permit', 50),
    ('CURRENT_LEGAL_STATUS', 'PERMANENT_RESIDENCE_PERMIT', 'Permanent residence permit', 60),
    ('CURRENT_LEGAL_STATUS', 'EU_LONG_TERM_RESIDENT', 'EU long-term resident permit', 70),
    ('CURRENT_LEGAL_STATUS', 'EU_RESIDENCE_REGISTRATION', 'EU/EEA/Swiss citizen residence registration', 80),
    ('CURRENT_LEGAL_STATUS', 'FAMILY_MEMBER_EU_CARD', 'Family member of an EU citizen residence card', 90),
    ('CURRENT_LEGAL_STATUS', 'TEMPORARY_PROTECTION', 'Temporary protection', 100),
    ('CURRENT_LEGAL_STATUS', 'REFUGEE_STATUS', 'Refugee status', 110),
    ('CURRENT_LEGAL_STATUS', 'SUBSIDIARY_PROTECTION', 'Subsidiary protection', 120),
    ('CURRENT_LEGAL_STATUS', 'PENDING_APPLICATION', 'I have a pending application', 130),
    ('CURRENT_LEGAL_STATUS', 'OTHER', 'Other', 140),
    ('CURRENT_LEGAL_STATUS', 'UNSURE', 'I am not sure', 150),

    -- PRIMARY_PURPOSE (curated subset of ASSESSMENT_DECISION_TREE.md Step 3)
    ('PRIMARY_PURPOSE', 'WORK', 'Work', 10),
    ('PRIMARY_PURPOSE', 'HIGHLY_QUALIFIED_WORK', 'Highly qualified work', 20),
    ('PRIMARY_PURPOSE', 'STUDY', 'Study', 30),
    ('PRIMARY_PURPOSE', 'JOIN_SPOUSE', 'Join my spouse', 40),
    ('PRIMARY_PURPOSE', 'JOIN_FAMILY_OTHER', 'Join other family', 50),
    ('PRIMARY_PURPOSE', 'LONG_TERM_STAY', 'Long-term stay', 60),
    ('PRIMARY_PURPOSE', 'PERMANENT_SETTLEMENT', 'Permanent settlement', 70),
    ('PRIMARY_PURPOSE', 'GET_PESEL', 'Get a PESEL number', 80),
    ('PRIMARY_PURPOSE', 'UNSURE', 'I am not sure yet', 90),

    -- EMPLOYMENT_CONTRACT_TYPE
    ('EMPLOYMENT_CONTRACT_TYPE', 'EMPLOYMENT_CONTRACT', 'Employment contract (umowa o prace)', 10),
    ('EMPLOYMENT_CONTRACT_TYPE', 'MANDATE_CONTRACT', 'Mandate contract (umowa zlecenie)', 20),
    ('EMPLOYMENT_CONTRACT_TYPE', 'B2B', 'Business-to-business contract', 30),
    ('EMPLOYMENT_CONTRACT_TYPE', 'OTHER', 'Other', 40),
    ('EMPLOYMENT_CONTRACT_TYPE', 'UNSURE', 'I am not sure', 50),

    -- STUDY_MODE
    ('STUDY_MODE', 'FULL_TIME', 'Full-time', 10),
    ('STUDY_MODE', 'PART_TIME', 'Part-time', 20),

    -- MARITAL_STATUS
    ('MARITAL_STATUS', 'SINGLE', 'Single', 10),
    ('MARITAL_STATUS', 'MARRIED', 'Married', 20),
    ('MARITAL_STATUS', 'OTHER', 'Other', 30)
) AS v(question_code, option_code, label, sort_order)
JOIN questions q ON q.code = v.question_code
JOIN questionnaire_questions qq ON qq.question_id = q.id
JOIN questionnaire_versions qv ON qv.id = qq.questionnaire_version_id
JOIN questionnaires quest ON quest.id = qv.questionnaire_id AND quest.code = 'WARSAW_GENERAL_ASSESSMENT';

-- --- QuestionDependency (branching - brief §13/§14/§70) ---

INSERT INTO question_dependencies (
    questionnaire_question_id, depends_on_questionnaire_question_id, operator, expected_value
)
SELECT gated.id, source.id, v.operator, v.expected_value::jsonb
FROM (VALUES
    ('CURRENT_LEGAL_STATUS',       'CURRENTLY_IN_POLAND', 'EQUALS',  'true'),
    ('CURRENT_COUNTRY',            'CURRENTLY_IN_POLAND', 'EQUALS',  'false'),
    ('CURRENT_STATUS_EXPIRY_DATE', 'CURRENT_LEGAL_STATUS', 'IN',
     '["TEMPORARY_RESIDENCE_PERMIT","PERMANENT_RESIDENCE_PERMIT","EU_LONG_TERM_RESIDENT","EU_RESIDENCE_REGISTRATION","FAMILY_MEMBER_EU_CARD","TEMPORARY_PROTECTION","REFUGEE_STATUS","SUBSIDIARY_PROTECTION"]'),
    ('HAS_JOB_OFFER',              'PRIMARY_PURPOSE', 'CONTAINS', '"WORK"'),
    ('HAS_JOB_OFFER',              'PRIMARY_PURPOSE', 'CONTAINS', '"HIGHLY_QUALIFIED_WORK"'),
    ('EMPLOYMENT_CONTRACT_TYPE',   'HAS_JOB_OFFER', 'EQUALS', 'true'),
    ('MONTHLY_GROSS_SALARY',       'HAS_JOB_OFFER', 'EQUALS', 'true'),
    ('HIGHLY_QUALIFIED',           'PRIMARY_PURPOSE', 'CONTAINS', '"HIGHLY_QUALIFIED_WORK"'),
    ('CURRENTLY_STUDYING',         'PRIMARY_PURPOSE', 'CONTAINS', '"STUDY"'),
    ('STUDY_MODE',                 'CURRENTLY_STUDYING', 'EQUALS', 'true'),
    ('EXPECTED_GRADUATION_DATE',   'CURRENTLY_STUDYING', 'EQUALS', 'true'),
    ('MARITAL_STATUS',             'PRIMARY_PURPOSE', 'CONTAINS', '"JOIN_SPOUSE"'),
    ('MARITAL_STATUS',             'PRIMARY_PURPOSE', 'CONTAINS', '"JOIN_FAMILY_OTHER"'),
    ('SPOUSE_CITIZENSHIP',         'MARITAL_STATUS', 'EQUALS', '"MARRIED"'),
    ('YEARS_IN_POLAND',            'PRIMARY_PURPOSE', 'CONTAINS', '"LONG_TERM_STAY"'),
    ('YEARS_IN_POLAND',            'PRIMARY_PURPOSE', 'CONTAINS', '"PERMANENT_SETTLEMENT"'),
    ('HAS_KARTA_POLAKA',           'PRIMARY_PURPOSE', 'CONTAINS', '"LONG_TERM_STAY"'),
    ('HAS_KARTA_POLAKA',           'PRIMARY_PURPOSE', 'CONTAINS', '"PERMANENT_SETTLEMENT"')
) AS v(gated_code, source_code, operator, expected_value)
JOIN questions gq ON gq.code = v.gated_code
JOIN questionnaire_questions gated ON gated.question_id = gq.id
JOIN questionnaire_versions gqv ON gqv.id = gated.questionnaire_version_id
JOIN questionnaires gquest ON gquest.id = gqv.questionnaire_id AND gquest.code = 'WARSAW_GENERAL_ASSESSMENT'
JOIN questions sq ON sq.code = v.source_code
JOIN questionnaire_questions source ON source.question_id = sq.id
JOIN questionnaire_versions sqv ON sqv.id = source.questionnaire_version_id AND sqv.id = gqv.id;
