# ADR-012: Admin content governance builds on the existing Phase 4-8 lifecycle, not a new engine

Status: Accepted — 2026-09-03

## Context

By Phase 9, `ProcedureVersion`, `RuleVersion`, `ThresholdVersion`, and `QuestionnaireVersion`
already each carried a full `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED` lifecycle
(`PublicationStateMachine`, shared since Phase 4/ADR-007), publish-readiness validation, and
actor/timestamp tracking - built specifically so a future admin phase would have real machinery
to expose, not placeholder plumbing (see `ProcedureAdminController`'s own Phase 4 Javadoc: "No
Angular admin UI exists for any of this - Phase 9's job"). The central design question Phase 9
faced: build a new, unified "content governance" engine, or expose what already existed and add
only the genuinely missing pieces (audit trail, review records, self-approval prevention, a
UI)?

## Decision

**Phase 9 adds a thin governance layer over the existing lifecycle, never a parallel one.**

- **`AuditLog`** (new, `com.foreignerwarsaw.common.audit`): one append-only table, one write
  path (`AuditService.record`), used by every admin mutation across all four content types
  alike - genuinely new, since nothing like it existed before Phase 9.
- **`AdminReview`** (new, `com.foreignerwarsaw.admin.review`): one entity-agnostic table
  (`entityType` + `entityVersionId`, no foreign key into any one specific version table)
  recording review decisions, plus **`ContentReviewCoordinator`** - the single place
  self-approval prevention is enforced, for all four content types, rather than reimplemented
  four times. Domain-specific publish-readiness validation (VERIFIED source requirements,
  condition-tree validity, dependency-graph acyclicity) stays exactly where it already lived
  (`ProcedurePublishingService`, `RulePublishingService`, `ThresholdService`,
  `QuestionnaireVersionService`) - this ADR does not move or duplicate that logic.
- **New admin API surface** (`/api/v1/admin/**`): list/detail/diff/impact/validate endpoints
  that never existed before, plus review-workflow actions that route through
  `ContentReviewCoordinator`. The pre-existing, already-tested Phase 4
  `/api/v1/internal/content/**` mutation endpoints for Procedure/Source are left completely
  unchanged - reused, not duplicated (brief §80).
- **New Angular admin UI** (`/admin/**`): the first UI this machinery has ever had.

## Consequences

- Self-approval prevention and review history are each implemented exactly once, shared by
  Procedure/Rule/Threshold/Questionnaire, rather than four times with four chances to drift.
- Content created through the legacy `/api/v1/internal/content/**` path is not retrofitted into
  the new audit trail - a documented gap (see
  [AUDIT_POLICY.md](../../admin/AUDIT_POLICY.md), PHASE_9_REPORT.md's Deviations), accepted
  rather than risk destabilizing Phase 4's own tested contract.
- A real, pre-existing structural asymmetry was found and only partly closed: `ThresholdVersion`
  never had a `version_number` column like its three siblings, and its publish-readiness check
  never required a VERIFIED source. Phase 9 adds the missing `submitted_by`/`archive()`
  parity fields (so the review workflow works identically across all four types) but
  deliberately does **not** invent a `version_number` or a source requirement for Threshold
  that never existed - see PHASE_9_REPORT.md's Known Issues for why, and the honest scope of
  what changed.

See [CONTENT_REVIEW_WORKFLOW.md](../../admin/CONTENT_REVIEW_WORKFLOW.md),
[ROLE_PERMISSIONS.md](../../admin/ROLE_PERMISSIONS.md),
[PUBLISHING_CHECKLIST.md](../../admin/PUBLISHING_CHECKLIST.md), and
[AUDIT_POLICY.md](../../admin/AUDIT_POLICY.md) for the concrete semantics this decision
produced.
