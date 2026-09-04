# Contributing

## Branching / PRs

Branch off `main`; never commit directly to `main`. One logical change per PR. Commit
messages describe the change and its reasoning, not just "fix bug."

## Tests

Every change ships with real, passing tests — no `@Disabled`/skipped test added to make
CI green. Run the full suite before opening a PR:

```bash
cd backend && ./mvnw verify
cd frontend && npm run lint && npm test -- --watch=false && npm run build
cd frontend && npm run e2e   # backend + Postgres + Mailpit must be running
```

## Formatting

Backend: `./mvnw spotless:apply` before committing — `./mvnw verify`'s own
`spotless:check` fails the build on a formatting violation, by design.
Frontend: `npm run lint` must be clean.

## Migration policy

Every schema change is a new Flyway migration under
`backend/src/main/resources/db/migration/` (`V<n>__description.sql`, next number after
the current highest) — never edit an already-applied migration, never
`ddl-auto=create`/`update` outside a justified test (ADR-002). Hibernate only ever
`validate`s against the schema Flyway owns.

## No hard-coded legal logic

Never write `if (nationality == "PK")` or `if (salary > 13000)` in application code —
that's a `CountrySpecificRule`/`Threshold` row, versioned and sourced in the database
(ARCHITECTURE.md §7–§8, ADR-003/ADR-004). Every requirement, fee, deadline, or document
shown to a user must trace to a real `OfficialSource`. See `CLAUDE.md`'s own standing
rules and `docs/product/PROCEDURE_CATALOGUE.md` for the sourcing/research workflow a
new procedure goes through before it's implementation-ready.

## Legal content governance

Legal/procedural content is never edited directly — it goes through the real Admin
workflow (draft → review → approve → publish, ADR-012), with role separation enforced
(the account that submits a version can never also approve it). No AI decides
eligibility (ADR-003) or auto-publishes a legal-content change (a human approves every
publish).

## No secrets

Never commit a real credential, API key, or `.env` file with real values — only
`.env.example`/`.env.*.example` templates belong in git. See `docs/security/` for the
project's security posture and `docs/privacy/` for its GDPR/data-handling model before
touching anything that processes personal data.

## Where to start

`docs/product/PROJECT_STATUS.md` is the current authoritative summary.
`docs/product/IMPLEMENTATION_PLAN.md` is the full phase-by-phase history.
`docs/architecture/ARCHITECTURE.md` and `docs/architecture/ADR/` explain why the system
is shaped the way it is.
