-- Phase 12 (Security/Privacy/GDPR) - canonical Phase 11 (Testing) inspection, carried into
-- Phase 12's own inspection, found that admin_review.submitted_by is NOT NULL ... ON DELETE
-- RESTRICT: any account that has ever submitted a Procedure/Rule/Threshold/Questionnaire
-- version for review could never be deleted at all, a real privacy-lifecycle blocker for
-- CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN accounts specifically.
--
-- Fix: add an immutable, pseudonymous submitter reference that survives account deletion,
-- decoupling governance-history integrity from the live User row. submitted_by itself
-- becomes nullable and ON DELETE SET NULL (matching reviewer's existing semantics one line
-- below it) - the review remains fully interpretable (who submitted it, by stable UUID,
-- distinct from a since-deleted account's email/name/profile) without blocking deletion.
ALTER TABLE admin_review ADD COLUMN submitted_by_actor_ref UUID;

-- Backfill: every existing review's pseudonymous ref starts equal to its current submitter -
-- a real UUID, never fabricated, matching this project's own "never invent data" discipline.
UPDATE admin_review SET submitted_by_actor_ref = submitted_by WHERE submitted_by_actor_ref IS NULL;

ALTER TABLE admin_review ALTER COLUMN submitted_by_actor_ref SET NOT NULL;

ALTER TABLE admin_review DROP CONSTRAINT admin_review_submitted_by_fkey;
ALTER TABLE admin_review ALTER COLUMN submitted_by DROP NOT NULL;
ALTER TABLE admin_review
    ADD CONSTRAINT admin_review_submitted_by_fkey
    FOREIGN KEY (submitted_by) REFERENCES users (id) ON DELETE SET NULL;
