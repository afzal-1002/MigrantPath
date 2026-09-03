# ADR-011: User cases are immutable snapshots, not live joins to Procedure content

Status: Accepted — 2026-09-03

## Context

Phase 7 hands the user a `Recommendation` - a categorical statement about which pathways
appear relevant. Phase 8 lets the user turn one into a persistent, personal "I'm working
on this" tracker with a checklist. The central design question: when the underlying
`ProcedureVersion` a case was created from later changes (a new fee, a changed document
requirement, a rewritten step), what happens to a case that already exists?

## Decision

**A `UserCase` snapshots its `ProcedureVersion`'s steps, documents, and fees into real,
case-owned rows at creation time** (`UserCaseSnapshotRevision` → `UserCaseStep`/
`UserCaseDocument`/`UserCaseFee`), rather than joining live to `ProcedureVersion`'s own
content on every read. A later republish of the `Procedure` never mutates an existing
case's checklist - the system instead offers a **detection** service
(`CaseRequirementChangeService`) that a user can consult, and an **explicit, user-
triggered upgrade** (`UserCaseUpgradeService`) that creates a *new* snapshot revision
while preserving as much progress as safely possible. This is the exact same reasoning
already applied to `UserCaseRequirementSnapshot` in the Phase 0 design sketch
(DATABASE.md §8) and to every other versioned-content boundary in this codebase
(ADR-004/ADR-007/ADR-009/ADR-010) - a user-facing state should never silently move
underneath the user.

**Revisions are append-only, matching Recommendation's own Phase 7 precedent (ADR-010)**:
an upgrade never edits `UserCaseSnapshotRevision` 1 in place, it creates revision 2 and
points the case at it, keeping revision 1 (and its items) permanently queryable.

**No JSONB snapshot blob alongside the relational rows** - a deliberate simplification of
the brief's suggested "hybrid" design (relational + JSONB), documented in
[CASE_SNAPSHOT_POLICY.md](../../cases/CASE_SNAPSHOT_POLICY.md#why-no-jsonb-snapshot-blob):
the relational rows already are the immutable provenance record; a parallel JSONB copy
would only risk drifting from them for no additional guarantee.

**Authorities/offices/official sources are resolved live at read time from the pinned
`procedure_id`/`procedure_version_id`, not duplicated into their own snapshot tables** -
because a published version's own source/authority/office *associations* never change
after the fact, resolving them fresh is exactly as reproducible as copying them, without
the extra schema. This mirrors Phase 7's `RecommendationSourceResolver` precedent exactly.

**No conditional-step/document/fee rule evaluation exists in Phase 8** - every step and
fee on the active version is snapshotted unconditionally; every document's applicability
is derived deterministically from its `RequirementType` (Phase 4), never from a raw
assessment answer read directly (brief §70). Phase 6's `STEP`/`DOCUMENT_REQUIREMENT`/`FEE`
rule targets exist in the schema but were never wired up to a resolvable target identity
(a known Phase 6 gap, documented there) - building conditional personalization on top of
an unresolvable target would mean fabricating logic in Java, exactly what this project's
own discipline forbids.

## Consequences

- Case creation does real, non-trivial work (copying N steps + M documents + K fees into
  new rows) rather than a cheap pointer assignment - accepted, since the alternative (a
  live join that could silently change under the user) is the one behavior this ADR
  exists to prevent.
- Requirement-change detection is O(steps + documents + fees) per check, computed on
  demand rather than cached or pushed - acceptable at Phase 8's expected case volume;
  revisit only if this becomes a measured hot path.
- A future conditional-personalization engine (once Phase 6's non-`PROCEDURE` rule
  targets are resolvable) plugs in at exactly one point - `UserCaseSnapshotService`'s
  applicability derivation - without needing to touch the revision/upgrade/change-
  detection machinery this ADR establishes.

See [USER_CASE_MODEL.md](../../cases/USER_CASE_MODEL.md),
[CASE_SNAPSHOT_POLICY.md](../../cases/CASE_SNAPSHOT_POLICY.md), and
[REQUIREMENT_CHANGE_POLICY.md](../../cases/REQUIREMENT_CHANGE_POLICY.md) for the concrete
schema/semantics this decision produced.
