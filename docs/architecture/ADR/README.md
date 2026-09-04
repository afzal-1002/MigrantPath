# Architecture Decision Records

Canonical Phase 15 (Release Readiness) — an index, added because none previously
existed. All 15 ADRs accounted for, in order; no gaps.

| ADR | Decision |
|---|---|
| [ADR-001](ADR-001-modular-monolith.md) | Modular monolith, not microservices |
| [ADR-002](ADR-002-postgresql.md) | PostgreSQL 18 as the system of record |
| [ADR-003](ADR-003-rules-engine.md) | Deterministic rules engine, not an LLM, decides eligibility |
| [ADR-004](ADR-004-versioned-legal-content.md) | Legal content is versioned data, never overwritten |
| [ADR-005](ADR-005-authentication-strategy.md) | Authentication strategy (cookie session + CSRF) |
| [ADR-006](ADR-006-country-classification.md) | Country classification — explicit group membership, derived free-movement status |
| [ADR-007](ADR-007-versioned-procedure-content.md) | Procedure content is a stable identity plus immutable, source-backed versions |
| [ADR-008](ADR-008-versioned-questionnaires.md) | The questionnaire is a versioned identity |
| [ADR-009](ADR-009-deterministic-condition-tree-engine.md) | A custom deterministic condition-tree evaluator, not Drools/SpEL |
| [ADR-010](ADR-010-recommendation-engine.md) | A separate, immutable, deterministic Recommendation Engine |
| [ADR-011](ADR-011-user-case-snapshots.md) | User cases are immutable snapshots, not live joins to Procedure content |
| [ADR-012](ADR-012-admin-content-governance.md) | Admin content governance builds on the existing content lifecycle |
| [ADR-013](ADR-013-production-deployment-architecture.md) | Production deployment architecture (Compose, reverse proxy, no Kubernetes) |
| [ADR-014](ADR-014-personal-data-lifecycle.md) | Personal data lifecycle — export, deletion, governance-safe FK design |
| [ADR-015](ADR-015-deployment-promotion-and-release-artifact-strategy.md) | Deployment promotion and release artifact strategy |

Each ADR is a standalone record of a decision at the time it was made — read the
current system state in [ARCHITECTURE.md](../ARCHITECTURE.md) and
[docs/product/PROJECT_STATUS.md](../../product/PROJECT_STATUS.md); an ADR is not
updated retroactively just because implementation details evolved, unless the
decision itself was reversed.
