# ADR-003: Deterministic rules engine, not an LLM, decides eligibility

Status: Accepted — 2026-09-01

## Context

Eligibility outcomes have real consequences for a user's immigration case. An LLM
cannot guarantee it applies the same legal conditions the same way every time, cannot be
audited condition-by-condition, and cannot cite which specific rule produced a
result — all required by the product's trust and traceability requirements.

## Decision

Eligibility is evaluated by a rules engine over versioned, database-stored
`Rule`/`RuleCondition`/`Threshold` data using a fixed operator vocabulary (`EQUALS`,
`IN`, `GREATER_THAN_OR_EQUAL`, `BETWEEN`, `DATE_AFTER`, `ALL`/`ANY`, etc.). Output is
categorical (`PRIMARY_MATCH` / `POSSIBLE_ALTERNATIVE` / `MORE_INFORMATION_REQUIRED` /
`NOT_APPLICABLE`) with matched/failed conditions and missing-information lists — never a
synthesized confidence percentage. If AI is introduced later, it explains or translates
engine output over approved content; it never becomes the decision-maker and never
publishes rule changes unreviewed.

## Consequences

- Every recommendation is explainable and traceable to specific rule versions and
  official sources.
- New eligibility logic requires modeling as data (rule rows), which is more upfront
  design work than "ask an LLM," but is the only approach compatible with auditability
  and legal-change versioning (ADR-004).
- Rule authoring/administration tooling (the Admin publish workflow) becomes a first-class
  product surface, not an afterthought.

See [ARCHITECTURE.md](../ARCHITECTURE.md) §7.
