-- Canonical Phase 13 (Deployment) brief §88/§202 - a repeatable, read-only production
-- data-quality precheck. Every query here is a SELECT; nothing mutates the database
-- (brief §88's own "do not mutate"). Run before a schema-affecting or content-affecting
-- release, and as part of the routine release checklist.
--
-- Usage:
--   docker exec -i <postgres-container> psql -U <db_user> -d <db_name> \
--     < infra/scripts/db-quality-check.sql
--
-- A clean release shows 0 rows under every "Overlapping..."/"Orphan.../"Self-approved.../
-- "TEST-content..." section and 0 under "Failed migrations". Any non-zero result is a
-- real finding to investigate before release, not something this script fixes itself.

\echo '=== Flyway: failed migrations (must be 0) ==='
SELECT version, description, installed_on
FROM flyway_schema_history
WHERE success = false;

\echo '=== Flyway: duplicate version numbers (must be 0 rows) ==='
SELECT version, count(*)
FROM flyway_schema_history
GROUP BY version
HAVING count(*) > 1;

-- The four versioned-content tables that carry their own independent status/
-- effective_from/effective_to lifecycle (procedure_versions, threshold_versions,
-- questionnaire_versions, rule_versions - document_requirement_versions/fee_versions/
-- step_versions are line items scoped entirely to a single procedure_version and have
-- no independent lifecycle of their own, so there is nothing to check for them here).
-- Each of the four already carries a DB-level EXCLUDE USING gist constraint preventing
-- two overlapping PUBLISHED rows for the same parent - these queries independently
-- verify that invariant by direct row inspection rather than trusting the constraint
-- alone (brief §202's own "no overlapping active versions").

\echo '=== Overlapping PUBLISHED procedure_versions (must be 0 rows - Active-Version Predicate) ==='
SELECT a.procedure_id, a.id AS version_a, b.id AS version_b
FROM procedure_versions a
JOIN procedure_versions b
  ON a.procedure_id = b.procedure_id AND a.id < b.id
WHERE a.status = 'PUBLISHED' AND b.status = 'PUBLISHED'
  AND daterange(a.effective_from, a.effective_to) && daterange(b.effective_from, b.effective_to);

\echo '=== Overlapping PUBLISHED rule_versions (must be 0 rows) ==='
SELECT a.rule_id, a.id AS version_a, b.id AS version_b
FROM rule_versions a
JOIN rule_versions b
  ON a.rule_id = b.rule_id AND a.id < b.id
WHERE a.status = 'PUBLISHED' AND b.status = 'PUBLISHED'
  AND daterange(a.effective_from, a.effective_to) && daterange(b.effective_from, b.effective_to);

\echo '=== Overlapping PUBLISHED threshold_versions (must be 0 rows) ==='
SELECT a.threshold_id, a.id AS version_a, b.id AS version_b
FROM threshold_versions a
JOIN threshold_versions b
  ON a.threshold_id = b.threshold_id AND a.id < b.id
WHERE a.status = 'PUBLISHED' AND b.status = 'PUBLISHED'
  AND daterange(a.effective_from, a.effective_to) && daterange(b.effective_from, b.effective_to);

\echo '=== Overlapping PUBLISHED questionnaire_versions (must be 0 rows) ==='
SELECT a.questionnaire_id, a.id AS version_a, b.id AS version_b
FROM questionnaire_versions a
JOIN questionnaire_versions b
  ON a.questionnaire_id = b.questionnaire_id AND a.id < b.id
WHERE a.status = 'PUBLISHED' AND b.status = 'PUBLISHED'
  AND daterange(a.effective_from, a.effective_to) && daterange(b.effective_from, b.effective_to);

\echo '=== Orphan user_cases (procedure/recommendation/assessment missing - must be 0 rows) ==='
-- FK constraints already make this structurally impossible (ON DELETE RESTRICT on all
-- three) - this query exists as an independent, defense-in-depth confirmation, not
-- because a gap is suspected.
SELECT uc.id
FROM user_cases uc
LEFT JOIN procedures p ON p.id = uc.procedure_id
LEFT JOIN recommendations r ON r.id = uc.recommendation_id
LEFT JOIN assessments a ON a.id = uc.assessment_id
WHERE p.id IS NULL OR r.id IS NULL OR a.id IS NULL;

\echo '=== Self-approved admin_review rows (reviewer = submitted_by_actor_ref - must be 0 rows) ==='
-- ContentReviewCoordinator.requireNotSelfReview should make this structurally
-- impossible going forward; this is the independent DB-level confirmation.
SELECT ar.id, ar.entity_type, ar.entity_version_id
FROM admin_review ar
JOIN users u ON u.id = ar.reviewer
WHERE ar.status IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED')
  AND u.id = ar.submitted_by_actor_ref;

\echo '=== Governance rows whose original submitter account has been deleted (informational - expect >=0, not an error) ==='
-- Real, expected once an account is deleted under Phase 12's governance-safe deletion
-- (submitted_by goes NULL via ON DELETE SET NULL; submitted_by_actor_ref, a pseudonym,
-- survives). Listed here for visibility, not as a failure condition.
SELECT count(*) AS reviews_with_deleted_submitter
FROM admin_review
WHERE submitted_by IS NULL;

\echo '=== TEST-content leakage in published procedures (must be 0 rows) ==='
SELECT p.id, p.code
FROM procedures p
JOIN procedure_versions pv ON pv.procedure_id = p.id
WHERE pv.status = 'PUBLISHED'
  AND (p.code ILIKE 'TEST%' OR p.code ILIKE '%_TEST' OR p.code ILIKE '%TEST_AUTHZ%');

\echo '=== PUBLISHED procedures with zero PUBLISHED steps (would 409 on case creation - see CaseCreationValidator; informational) ==='
SELECT p.code
FROM procedures p
JOIN procedure_versions pv ON pv.procedure_id = p.id AND pv.status = 'PUBLISHED'
LEFT JOIN step_versions sv ON sv.procedure_version_id = pv.id
GROUP BY p.code
HAVING count(sv.id) = 0;

\echo '=== Row counts (informational baseline) ==='
SELECT 'users' AS table_name, count(*) FROM users
UNION ALL SELECT 'procedures', count(*) FROM procedures
UNION ALL SELECT 'procedure_versions (published)', count(*) FROM procedure_versions WHERE status = 'PUBLISHED'
UNION ALL SELECT 'rule_versions (published)', count(*) FROM rule_versions WHERE status = 'PUBLISHED'
UNION ALL SELECT 'user_cases', count(*) FROM user_cases
UNION ALL SELECT 'admin_review', count(*) FROM admin_review
UNION ALL SELECT 'audit_log', count(*) FROM audit_log
UNION ALL SELECT 'flyway_schema_history', count(*) FROM flyway_schema_history;
