# ADR-004: Legal content is versioned data, never overwritten

Status: Accepted — 2026-09-01

## Context

Immigration requirements, fees, and thresholds change over time and by effective date.
A user's case must remain explainable against the rules that applied when it was
created, even after the procedure is updated. Silently mutating a live case when
requirements change would misrepresent what the user was told and is explicitly
prohibited by the product's trust requirements.

## Decision

`Procedure`, `Rule`, `DocumentRequirement`, `Fee`, and `Threshold` all have versioned
counterparts (`ProcedureVersion`, `RuleVersion`, `DocumentRequirementVersion`,
`FeeVersion`, `ThresholdVersion`) carrying `effectiveFrom`/`effectiveTo`/`version`/
`status`/`sourceId`. A change is always a new version row, never an `UPDATE` of the old
one. A `UserCase` records which version generated it and, if the procedure is
republished, flags "requirements have changed" with a reviewable diff instead of
silently updating.

## Consequences

- Full history is queryable for any point in time — what a user was told, and when.
- Every version links to an `OfficialSource`, making the traceability requirement
  (ADR-003, §22 sourcing) enforceable at the schema level.
- Storage and query complexity increase (joins against "current version" rather than a
  flat table) — mitigated by narrow, well-indexed version-lookup queries and by treating
  "get the active version" as a single well-tested repository method per entity.

See [ARCHITECTURE.md](../ARCHITECTURE.md) §5, §8.
