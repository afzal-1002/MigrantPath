# ADR-002: PostgreSQL 18 as the system of record

Status: Accepted — 2026-09-01

## Context

The domain is heavily relational (users, procedures, versioned rules, cases, sources
with foreign-key integrity requirements) with one genuinely flexible sub-structure — the
rule condition tree.

## Decision

PostgreSQL 18, managed via Flyway migrations, `spring.jpa.hibernate.ddl-auto=validate`
in every environment (never `create`/`update`). JSONB is used narrowly for the
`RuleCondition` tree where a fully normalized table would just reimplement a JSON tree
with extra joins; everything else is normalized.

## Consequences

- Referential integrity (e.g. preventing deletion of an `OfficialSource` referenced by a
  published `Rule`) is enforced by real FK/check constraints, not just application code.
- Schema evolves only through committed migration files — reviewable, repeatable,
  reproducible in Testcontainers-based integration tests.
- JSONB usage is deliberately scoped; if it's tempting to reach for JSONB elsewhere,
  that's a signal the schema needs a real table instead.

See [ARCHITECTURE.md](../ARCHITECTURE.md) §4.
