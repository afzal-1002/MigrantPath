# Phase 3 Completion Report — Reference + Geographic Data

Date: 2026-09-02

## Architecture

```
Country ──< CountryGroupMembership >── CountryGroup            (raw membership facts;
                                                                  free-movement-group status derived, never stored)

Country ──< Region ──< City ──< District                        (geography: where a place is)

Jurisdiction (self-referencing tree: NATIONAL → REGIONAL → MUNICIPAL)
  ├─ denormalized region_id / city_id (direct lookup, no recursive walk needed)
  └──< Authority (self-referencing parent_authority_id, unused in Phase 3)
         └──< Office ──< OfficeService >── ServiceType          (routing/reference tags only)
```

Two layers that are easy to conflate but serve different purposes (ARCHITECTURE.md §9):
**geography** (`Country`/`Region`/`City`/`District`) is *where a place is*; **jurisdiction**
is *at what legal/procedural scope a rule, procedure, or authority operates*. Phase 0
sketched Jurisdiction as three independent nullable FKs; Phase 3 refined it into a
self-referencing tree (`parent_jurisdiction_id`) — the concrete driver was needing to walk
"Warsaw → its parent Mazowieckie → its parent Poland" without three separate lookups —
while keeping `region_id`/`city_id` denormalized on the row itself so "find the
jurisdiction for Warsaw" stays a plain indexed lookup, not a recursive query. `Authority`
carries the same self-referencing shape (`parent_authority_id`) for a future sub-office
hierarchy, unused by any Phase 3 seed row.

`Country`/`Region`/`City`/`District`/`Authority`/`Office` are all plain mutable rows with
`valid_from`/`valid_to` — never the full identity+version pattern legal content uses
(ADR-004). An office's address is an operational fact admins correct, not a legal
position needing a DRAFT→PUBLISHED review workflow.

Full schema detail: [DATABASE.md §2](../database/DATABASE.md#2-geographic--reference-entities)
(rewritten this phase from Phase 0's speculative sketch to match the actual implementation).

## Free-Movement-Group Classification (originally "Third-Country Decision" — renamed, see Post-Approval Audit below)

No `THIRD_COUNTRY` group is **ever stored** — no row for it exists in `country_groups`.
`CountryClassificationService.isOutsideEuEeaSwissFreeMovementGroup(code, date)` (originally
named `isThirdCountry`) returns true iff the country has no active membership in
`EU_MEMBER`, `EEA`, or `EFTA` as of that date (Switzerland is covered via its `EFTA`
membership, no special case needed). This keeps the result always correctly derived as new
group memberships are added, without ever having to remember to update a stored row when
e.g. a country joins the EU.

**This is a narrow structural fact about country-group membership, not a universal legal
"third-country national" determination** — different EU/Polish legal instruments define
that term differently (e.g. around Swiss nationals or UK Withdrawal Agreement
beneficiaries), so a future Phase 6 rule must apply the specific legal definition its own
procedure requires, using this fact as one input among several. See ADR-006's "Why not a
universal legal boolean" section.

`UK_WITHDRAWAL_AGREEMENT` is likewise **never** a `CountryGroup` row: it's a
person-level fact (an individual UK national's specific residence-rights status under the
Withdrawal Agreement), not a country-level one — modeling it as a country membership would
incorrectly imply every UK national shares it. Full rationale, including the temporal
convention (`valid_to` **inclusive**, deliberately different from the legal-content Active
-Version Predicate's exclusive `effective_to`) is in
[ADR-006](../architecture/ADR/ADR-006-country-classification.md).

Verified against the real historical case this design exists for: `GB`'s `EU_MEMBER`
membership (`1973-01-01`..`2020-01-31`) is present in `classificationsFor("GB", 2019-06-01)`
and absent in `classificationsFor("GB", 2021-06-01)` — proven at three layers (mocked-service
unit test, real-database repository test, and a live HTTP call during manual verification).
Switzerland/Iceland/Liechtenstein/Norway's EFTA-vs-EEA distinction, and Schengen's
independence from this classification, are proven against real seed data — see Testing
below.

## Seed Data

| Table | Count | Notes |
|---|---|---|
| `countries` | 250 | 249 officially assigned ISO 3166-1 codes + 1 user-assigned (`XK`, Kosovo) — see Post-Approval Audit |
| `country_groups` | 5 | `EU_MEMBER`, `EEA`, `EFTA`, `SCHENGEN` (LEGAL), `EU_EEA_SWISS` (CONVENIENCE) |
| `country_group_memberships` | 123 | Current + historical; UK's ended `EU_MEMBER` row included |
| `regions` | 16 | All Polish voivodeships |
| `cities` | 1 | Warsaw only (`active = true`) — the only enabled city in V1 |
| `districts` | 18 | All official Warsaw districts (dzielnice), Polish diacritics preserved |
| `jurisdictions` | 3 | `PL` (NATIONAL) → `PL_MAZOWIECKIE` (REGIONAL) → `PL_MAZOWIECKIE_WARSAW` (MUNICIPAL) |
| `authorities` | 3 | One per jurisdiction level: UDSC (national), Mazowieckie Voivodeship Office (regional), Warsaw City Hall (municipal) |
| `service_types` | 4 | `PESEL`, `MELDUNEK`, `DRIVING_LICENCE`, `IMMIGRATION_INFORMATION` |
| `offices` | 1 | Wydział Spraw Cudzoziemców (Mazowiecki Urząd Wojewódzki) — the only office record re-verified against its primary source this phase |
| `office_services` | 1 | The above office tagged `IMMIGRATION_INFORMATION` |

## Database

11 new Flyway migrations, `V7`–`V17` (`backend/src/main/resources/db/migration/`):

| Migration | Contents |
|---|---|
| V7 | `countries` table |
| V8 | Seeds all 250 countries |
| V9 | `country_groups`, `country_group_memberships` |
| V10 | Seeds the 5 country groups |
| V11 | Seeds 123 memberships (current + historical) |
| V12 | `regions`, `cities`, `districts` |
| V13 | Seeds 16 voivodeships, Warsaw, 18 Warsaw districts |
| V14 | `jurisdictions` (self-referencing, with the NATIONAL/REGIONAL/MUNICIPAL `CHECK` constraint) |
| V15 | Seeds the PL → PL_MAZOWIECKIE → PL_MAZOWIECKIE_WARSAW chain |
| V16 | `authorities`, `service_types`, `offices`, `office_services` |
| V17 | Seeds 3 authorities, 4 service types, 1 office, 1 office-service link |
| V18 | Post-approval audit fix: adds `code_standard`/`officially_assigned`/`notes` to `countries`; marks `XK` `USER_ASSIGNED`/not officially assigned |
| V19 | Post-approval audit fix: adds `provenance_status` to `country_group_memberships`; marks every `valid_from < 2000-01-01` row `DRAFT` |

Verified applying cleanly against a fresh PostgreSQL 18 instance (both a from-empty
`docker compose` volume and a fresh Testcontainers container per test run) — all 19
migrations (V1–V19) apply in order with no manual intervention.

## APIs

All under `/api/v1/reference/**`, public (no session/CSRF required), GET-only — no write
endpoint exists under this prefix at all in Phase 3 (admin editing is Phase 9's job):

```
GET /api/v1/reference/countries                        all active countries, code + name
GET /api/v1/reference/countries/{code}                  one country + its active groups as of today
GET /api/v1/reference/countries/{code}/regions          active regions for a country
GET /api/v1/reference/regions/{code}/cities             active cities for a region
GET /api/v1/reference/cities/{code}/districts           active districts for a city
GET /api/v1/reference/authorities?jurisdiction=&city=&authorityType=
GET /api/v1/reference/offices?city=&district=&authority=&service=
```

Documented via springdoc `@Operation`/`@Tag` annotations (Swagger UI, local/staging only).

## Frontend

- **`ReferenceDataService`** (`core/services/reference-data.service.ts`) — typed HTTP
  client for all six read-only endpoints above; no write methods, no client-side caching
  (the backend already caches the active-countries list).
- **`CountrySelect`** (`shared/country-select/`) — reusable, searchable, code-based
  country picker built on `MatAutocomplete` (accessible combobox semantics by
  construction) and `ControlValueAccessor` (usable as a plain `formControlName` anywhere).
  The bound form value is always the ISO code, never the display name.
- **`ReferenceDemo`** (`features/reference-demo/`, route `/reference-demo`) — a Phase 3
  verification page (not a product route) proving the reference API end to end: a
  country picker plus a region → city → district cascading select, all backed by real
  HTTP calls.

## Data Sources

Full provenance record (dataset/authority, URL, date checked, status):
[REFERENCE_DATA_SOURCES.md](../reference/REFERENCE_DATA_SOURCES.md). Summary:

| Data | Source | Status |
|---|---|---|
| ISO countries | mledoze/countries (ODbL-1.0), derived from ISO 3166-1 | VERIFIED |
| EU/EEA/EFTA/Schengen current membership | EU/EFTA/Schengen official membership | VERIFIED |
| EU/EEA/EFTA/Schengen pre-2000 accession dates | Compiled from general enlargement history | DRAFT — flagged for a dedicated verification pass before Phase 4 relies on historical dates |
| Polish voivodeships | 1999 territorial reform | VERIFIED |
| Warsaw official districts | 2002 municipal reform | VERIFIED |
| Authority identities (UDSC, Mazowieckie Voivodeship Office, Warsaw City Hall) | Official websites | VERIFIED |
| The one seeded office address | gov.pl/web/uw-mazowiecki, re-verified 2026-09-02 | VERIFIED |
| Office opening hours | — | NOT POPULATED (no verified source this phase) |

## Testing

**Backend** — 104 tests total (44 Phase 1/2 + 60 new Phase 3), all passing:
- 30 repository tests against real Testcontainers PostgreSQL 18 (`CountryRepositoryTest`,
  `CountryGroupMembershipRepositoryTest`, `GeographyRepositoryTest`,
  `JurisdictionRepositoryTest`, `AuthorityOfficeRepositoryTest`) — including two
  regression tests that specifically reproduce the two `LazyInitializationException`
  bugs found during this phase's own manual verification (see Deviations) by asserting
  the fix survives a persistence-context closure.
- 18 mocked-repository service tests (`CountryServiceTest`,
  `CountryClassificationServiceTest` — including the exact historical UK/Brexit case,
  `GeographyServiceTest`, `AuthorityServiceTest`, `OfficeLookupServiceTest`).
- 12 full-stack API integration tests through the real Spring Security filter chain
  (`ReferenceApiIntegrationTest`) — public 200s, 404 for an unknown country, exactly 16
  regions / 18 districts, no write capability exposed, and two explicit Phase 2
  regression checks (`/api/v1/users/me` still 401 unauthenticated,
  `/api/v1/platform/status` still public).

**Phase 1/2 regression**: all 44 pre-existing backend tests still pass unmodified — `./mvnw
clean verify` is green end to end (build, all 104 tests, jar packaging, Spotless format
check).

**Frontend** — 40 unit tests total (23 Phase 1/2 + 17 new), all passing; `ng lint` clean;
production build succeeds (`reference-demo` correctly lazy-chunked).

**Playwright E2E** — 7 scenarios total (6 Phase 1/2 + 1 new), all passing against the real
backend/database/dev-server. The new scenario is the exact one named in this phase's
brief: selecting Pakistan then Poland → Mazowieckie → Warsaw resolves the real, live
18-district list in a real browser — not a hardcoded fixture. This test is also what
caught a real bug (see Deviations).

**Data-quality validation** (direct SQL against the live database): 0 duplicate codes in
any reference table, 0 orphaned `country_group_memberships`, 0 invalid validity ranges,
exactly 18 active Warsaw districts, Warsaw → Mazowieckie → Poland chain confirmed, 0
districts referencing the wrong city.

## Verification commands executed

```bash
# Backend, full suite, from a clean build
cd backend && ./mvnw clean verify
# → BUILD SUCCESS, Tests run: 104, Failures: 0, Errors: 0, Spotless clean

# Frontend
cd frontend && npm run lint && npm test -- --watch=false && npm run build
# → lint clean; Test Files 11 passed (11), Tests 40 passed (40); build succeeds

# End-to-end
cd frontend && npx playwright test
# → 7 passed

# Manual API verification against a freshly-migrated (docker compose down -v && up -d)
# PostgreSQL instance
curl /api/v1/reference/countries              # 250 entries
curl /api/v1/reference/countries/PL           # groups: EU_MEMBER, EEA, SCHENGEN, EU_EEA_SWISS
curl /api/v1/reference/countries/GB           # groups: [] (post-Brexit, as of today)
curl /api/v1/reference/countries/ZZ           # 404 COUNTRY_NOT_FOUND
curl /api/v1/reference/countries/PL/regions   # 16 entries
curl /api/v1/reference/regions/MAZOWIECKIE/cities   # [WARSAW]
curl /api/v1/reference/cities/WARSAW/districts      # 18 entries, correct diacritics
curl /api/v1/reference/authorities            # all 3, across all 3 jurisdiction levels
curl /api/v1/reference/offices                # the 1 seeded office, with its service

# Direct SQL data-quality pass (see Testing section above) against the docker-compose
# PostgreSQL instance
```

## Deviations

1. **Country group vocabulary**: implemented as `EU_MEMBER` / `EEA` / `EFTA` / `SCHENGEN`
   (split, `LEGAL`-typed) plus `EU_EEA_SWISS` (a `CONVENIENCE`-typed aggregate) — not the
   originally-sketched `EEA_EFTA` (merged) / `UK_WITHDRAWAL_AGREEMENT`. The withdrawal
   agreement is a person-level fact, not a country-level one, and can't be modeled as a
   `CountryGroup` membership at all — see ADR-006 and IMPLEMENTATION_PLAN.md 3.4.
2. **Jurisdiction refined into a self-referencing tree**, not the three-independent-FK
   flat design Phase 0 originally sketched — see Architecture above and DATABASE.md §2.
3. **`ServiceType` promoted to its own reference entity**, not a bare `service_code`
   string on the `OfficeService` join row as Phase 0 sketched — see DATABASE.md §2 and
   IMPLEMENTATION_PLAN.md 3.9.
4. **Only one office seeded**, not "the Mazowieckie office plus one Warsaw district
   delegation" as originally planned (IMPLEMENTATION_PLAN.md 3.10) — the brief's own
   "seed fewer records rather than inventing data" instruction took priority once no
   second office address could be independently re-verified in this pass.
5. **Two real bugs found and fixed during this phase's own manual verification** (not
   deviations from the plan, but worth naming explicitly per the brief's "prove it, don't
   assume it" standard):
   - `AuthorityRepository.search`/`OfficeRepository.search` originally returned entities
     with un-fetched lazy associations, causing a `LazyInitializationException` once the
     transactional service method returned and the controller mapped them to DTOs. Fixed
     with explicit `JOIN FETCH`.
   - Separately, `AuthorityRepository.search`'s city filter used an inline `j.city.code`
     path expression, which JPQL compiles to an *implicit inner join* — silently dropping
     every `REGIONAL`-jurisdiction authority (whose jurisdiction has `city_id IS NULL` by
     the `CHECK` constraint) even when the `city` filter parameter was null (i.e. meant
     to be a no-op). Fixed with an explicit `LEFT JOIN`. Both bugs now have dedicated
     regression tests (`AuthorityOfficeRepositoryTest`).
   - Separately, the frontend's `ReferenceDataService`/`CountrySelect`/`ReferenceDemo`
     interfaces initially used `canonicalName` as the display-name field, but every
     backend DTO actually serializes it as `name` — invisible to unit tests (which mocked
     HTTP responses using the same wrong field name), caught only by the real Playwright
     E2E test hitting the real backend. Fixed across all five affected files.

## Known Issues

- EU/EEA/EFTA/Schengen accession dates before ~2000 are `DRAFT` — now a real, queryable
  `provenance_status` column (V19, post-approval audit) rather than only a migration
  comment, but still not independently re-verified against a single authoritative
  timeline. Flagged in REFERENCE_DATA_SOURCES.md for a dedicated pass; does not block
  Phase 4 (no engineering task depends on pre-2000 date precision), and a future
  legally-significant rule evaluation can require `VERIFIED` provenance where it matters
  (ADR-006).
- Office `opening_hours` is unpopulated for the one seeded office (JSONB column exists,
  deliberately empty — no verified source checked specifically for current hours).
- District-level office routing for PESEL/meldunek/driving-licence (which specific
  Warsaw district office handles which case) is not modeled at all yet — explicitly
  deferred to Phase 10, consistent with the brief's own instruction not to guess at
  applicant-dependent routing.
- `ProcedureOffice` (linking a specific office to a specific procedure) is not
  implemented — correctly deferred, since no `Procedure` identity exists yet for it to
  reference (arrives in Phase 4).
- `Authority.parent_authority_id` exists in the schema but has no populated example yet
  (no verified sub-office hierarchy to seed) — the association and its JPA getter are in
  place and covered by a repository test using manufactured fixtures, but real seed data
  demonstrating it doesn't exist until a concrete case arises.

## Post-Approval Audit (2026-09-02)

Two issues raised after initial Phase 3 approval, before the git checkpoint — both fixed,
tested, and documented in place above (this section records what changed and why, rather
than duplicating it):

1. **Country count audit.** The reported `countries = 250` was correct as a row count but
   imprecise: ISO 3166-1 currently has 249 officially assigned alpha-2 codes, not 250.
   Confirmed directly against Wikipedia's ISO 3166-1 alpha-2 article. The 250th row is
   `XK` (Kosovo) — a user-assigned code the ISO 3166 Maintenance Agency has never
   assigned, included by the source `mledoze/countries` dataset following the same
   convention used by the European Commission/IMF/SWIFT/CLDR. **Kosovo is kept**, not
   removed to force the count to 249 — it's operationally useful for this application —
   but is now modeled honestly: `Country.code_standard`/`officially_assigned`/`notes`
   (V18) make this a real, queryable fact instead of an undocumented row indistinguishable
   from the other 249. Proven by
   `CountryRepositoryTest#seedData_exactlyOneRowIsNotAnOfficiallyAssignedIsoCode_andItIsKosovo`
   and exposed through the public API (`CountryDetailResponse.codeStandard`/
   `officiallyAssigned`, proven by `ReferenceApiIntegrationTest`).
2. **THIRD_COUNTRY semantics refined.** `CountryClassificationService.isThirdCountry` was
   renamed to `isOutsideEuEeaSwissFreeMovementGroup` — the original name risked being
   misread as a universal legal "third-country national" definition, when different
   EU/Polish legal instruments actually define that term differently (e.g. treatment of
   Swiss nationals or UK Withdrawal Agreement beneficiaries). The renamed method still
   does exactly the same computation (no behavior change) but its Javadoc, and a new ADR-006
   section ("Why not a universal legal boolean"), now make explicit that this is a
   structural fact about country-group membership only — Phase 6 must apply whichever
   legal definition a specific procedure actually requires, using this as one input among
   several, never as the whole answer. `EFTA`≠`EEA` (Switzerland: `EFTA` only; Iceland/
   Liechtenstein/Norway: both) and Schengen's independence from any free-movement
   classification are now both proven against real seed data
   (`CountryGroupMembershipRepositoryTest`, `CountryClassificationServiceTest`).
   `CountryGroupMembership.provenance_status` (V19) additionally makes the existing
   pre-2000-accession-date DRAFT/VERIFIED distinction a real column rather than only a
   migration comment.

**Regression**: full backend (`./mvnw clean verify`), frontend (lint/test/build), and
Playwright suites all re-run green after these changes — see the chat response
accompanying this audit for the exact counts, since they're a point-in-time delta on top
of the numbers already recorded above.

## Phase 4 Readiness: READY

Reference and jurisdiction data — the foundation the procedure, questionnaire, rules, and
recommendation engines will depend on — is fully implemented, seeded, tested (104 backend
+ 40 frontend + 7 E2E, all passing), and documented. No known defect blocks Phase 4 work.
The working tree is uncommitted, ready for the checkpoint commit once this report is
reviewed and approved — mirroring the Phase 2→3 transition.
