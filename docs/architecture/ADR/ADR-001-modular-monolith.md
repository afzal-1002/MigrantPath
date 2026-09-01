# ADR-001: Modular monolith, not microservices

Status: Accepted — 2026-09-01

## Context

The product is a new SaaS with no users yet. Microservices add operational overhead
(deployment, service discovery, distributed transactions, observability across
services) that isn't justified before the product or team has grown into it.

## Decision

Single Spring Boot backend, package-by-feature (`auth`, `user`, `reference`,
`assessment`, `rules`, `recommendation`, `procedure`, `case`, `source`, `notification`,
`admin`, `audit`, `common`), each with its own `controller`/`service`/`domain`/
`repository`/`dto`/`mapper`. One Angular frontend, feature-based structure with lazy
loading.

## Consequences

- Single deployment unit simplifies CI/CD, transactions, and local development.
- Module boundaries are enforced by convention/code review, not process isolation — an
  errant import can couple modules that shouldn't be coupled. Package-by-feature and
  keeping legal content out of Java (ADR-004) mitigate this.
- If a specific module (e.g. `rules`) later needs independent scaling, it's extractable
  because it doesn't reach into other modules' internals.

See [ARCHITECTURE.md](../ARCHITECTURE.md) §1.
