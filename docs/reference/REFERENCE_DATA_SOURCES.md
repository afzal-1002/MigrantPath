# Reference Data Sources — Foreigner Warsaw

Status: Phase 3
Last updated: 2026-09-02

Every fact seeded into the `reference.*` tables (`docs/database/DATABASE.md` §2) traces
back to a named source below, with the date it was checked and its confidence status
(`VERIFIED` / `DRAFT` — mirroring the `VERIFIED`/`NEEDS_REVIEW` vocabulary
`OfficialSource` uses for legal content in §3, applied here to reference data instead).
This is a living record: a re-check of any row updates its "Date checked" here, the same
way `OfficialSource.last_checked_at` works for procedure content.

## 1. ISO country list (`countries`, V7/V8, `code_standard`/`officially_assigned` added V18)

| | |
|---|---|
| **Dataset** | [mledoze/countries](https://github.com/mledoze/countries) |
| **License** | ODbL-1.0 (Open Database License) — attribution required, share-alike on the database itself; permits this kind of derived/seeded use |
| **Upstream basis** | ISO 3166-1 (alpha-2, alpha-3, numeric codes) |
| **Date checked** | 2026-09-01; audited again 2026-09-02 specifically for the count below |
| **Status** | VERIFIED |
| **Notes** | 250 rows total, but **249 officially assigned ISO 3166-1 alpha-2 codes plus exactly one non-ISO row**: `XK` (Kosovo). Confirmed directly against Wikipedia's "ISO 3166-1 alpha-2" article on 2026-09-02, which states there are 249 current officially assigned codes and describes `XK` as a user-assigned/temporary code "used by the European Commission, the IMF, the SWIFT, the CLDR, and other organizations" — never assigned by the ISO 3166 Maintenance Agency. The `mledoze/countries` dataset includes `XK` following that same widespread convention. `alpha3_code = 'UNK'` for the `XK` row is equally an unofficial placeholder, not an ISO 3166-1 alpha-3 assignment. **Kosovo is kept, not removed** — it's operationally useful for this application (e.g. as a country of citizenship) — but is now modeled honestly: `code_standard = 'USER_ASSIGNED'` and `officially_assigned = false` for that one row only (V18); every other row is `code_standard = 'ISO_3166_1'`/`officially_assigned = true`. Proven by `CountryRepositoryTest#seedData_exactlyOneRowIsNotAnOfficiallyAssignedIsoCode_andItIsKosovo`. `display_order` is a deterministic alphabetical-by-common-name rank computed at seed time, not upstream data. No legal/political classification is carried from this source — that's §2/§3 below, entirely separate tables. |

## 2. EU / EEA / EFTA / Schengen membership (`country_groups`, `country_group_memberships`, V10/V11; `provenance_status` added V19)

| | |
|---|---|
| **Authority** | European Union (EU membership, EEA), European Free Trade Association (EFTA), Schengen *acquis* signatory states |
| **Date checked** | 2026-09-01 |
| **Status** | **Current membership: VERIFIED.** **Pre-2000 accession dates: DRAFT** — now a real, queryable `provenance_status` column (V19), not only this document's own note — pending a dedicated verification pass against a single authoritative timeline (see `V11`'s own migration comment). Compiled from general knowledge of EU/EEA/EFTA/Schengen enlargement history rather than one primary source per date. What matters most today (which countries are members *right now*) is accurate; the flagged risk is date *precision* for historical evaluation dates before ~2000, not membership itself. This DRAFT status does **not** block Phase 4 — see ADR-006's "Membership provenance" section for how a future legally-significant rule can require `VERIFIED` provenance where it matters. |
| **Notes** | The historical UK row (`GB, EU_MEMBER, 1973-01-01..2020-01-31`, `valid_to` inclusive per ADR-006) is the specific case this data model exists to get right — see [ADR-006](../architecture/ADR/ADR-006-country-classification.md). Bulgaria/Romania's Schengen accession (2025-01-01) and Croatia's (2023-01-01) are the two most recent, real dates in this table. `EU_EEA_SWISS` is an application-defined `CONVENIENCE` grouping (not an EU-law category) recording Poland's common administrative practice of treating EU/EEA/Swiss nationals equivalently for certain purposes (e.g. driving licence recognition) — Switzerland's row cites the 1999 EU-Swiss bilateral free-movement agreement, in force 2002-06-01, as the actual legal basis, not EU/EEA membership it doesn't hold. **`EFTA` and `EEA` are distinct groups, not aliases**: Switzerland holds only `EFTA` (never `EEA`); Iceland, Liechtenstein, and Norway hold both — proven by real-seed-data tests in `CountryGroupMembershipRepositoryTest`. **`SCHENGEN` is independent reference information** — border-control cooperation, never a residence-rights signal on its own; no classification derives residence rights from Schengen membership alone (see ADR-006). |

## 3. Polish regions — voivodeships (`regions`, V13)

| | |
|---|---|
| **Authority** | Act on the introduction of the basic three-tier territorial division of the state (Poland), in force since the 1999-01-01 reform |
| **Date checked** | 2026-09-01 |
| **Status** | VERIFIED |
| **Notes** | All 16 current voivodeships are seeded (`region_type = 'VOIVODESHIP'`), not just Mazowieckie — stable, small reference data, seeded ahead of need to de-risk future city expansion (brief §26's recommendation). Only Mazowieckie carries downstream operational data (cities/authorities/offices) in Phase 3. |

## 4. Warsaw — city and official districts (`cities`, `districts`, V13)

| | |
|---|---|
| **Authority** | City of Warsaw (Miasto Stołeczne Warszawa) — official district (*dzielnica*) division, in force since the 2002 municipal reform |
| **Date checked** | 2026-09-01 |
| **Status** | VERIFIED |
| **Notes** | All 18 official districts seeded with Polish diacritics preserved in `canonical_name` (Białołęka, Praga-Południe, Praga-Północ, Śródmieście, Żoliborz, ...) — display names are never ASCII-normalized (brief §62), verified end-to-end through the real HTTP API and browser rendering, not just the database. Warsaw is the only `City` row with `active = true` in Phase 3 (ARCHITECTURE.md §9). |

## 5. Authorities and offices (`authorities`, `offices`, `service_types`, `office_services`, V17)

| | |
|---|---|
| **Authorities seeded** | `UDSC` (Urząd do Spraw Cudzoziemców / Office for Foreigners, national), `MAZOWIECKIE_VOIVODESHIP_OFFICE` (Mazowiecki Urząd Wojewódzki w Warszawie, regional), `WARSAW_CITY_HALL` (Miasto Stołeczne Warszawa, municipal) |
| **Date checked** | 2026-09-01 (identity/website only — see office note below) |
| **Status** | VERIFIED (identity/jurisdiction), one office address re-verified — see below |
| **Sources** | [udsc.gov.pl](https://udsc.gov.pl), [gov.pl/web/uw-mazowiecki](https://www.gov.pl/web/uw-mazowiecki), [warszawa19115.pl](https://warszawa19115.pl) |
| **Notes** | Conservative, verified-only seeding (brief §48): three authorities whose institutional identity is well-established, but only **one** physical office record seeded in Phase 3 — the one address actually re-verified against its primary source in this pass. Warsaw district-level office routing for PESEL/meldunek/driving-licence (genuinely complex, applicant-dependent — brief §19) is deliberately deferred to Phase 10 rather than guessed at now. |

### The one seeded office

| | |
|---|---|
| **Office** | Wydział Spraw Cudzoziemców (Department for Foreigners' Affairs), Mazowiecki Urząd Wojewódzki |
| **Source URL** | https://www.gov.pl/web/uw-mazowiecki/wydzial-spraw-cudzoziemcow |
| **Date verified** | 2026-09-02 |
| **Status** | VERIFIED (`offices.source_url`/`last_verified_at` populated — the only office row in Phase 3 with both set) |
| **Notes** | Address: ul. Marszałkowska 3/5, 00-624 Warszawa. Other addresses (e.g. Krucza 5/11, Plac Bankowy 3/5) appear in secondary sources for specific sub-services (fingerprints, card collection, general MUW information point) but were **not** independently verified in this pass — confirm the applicant's specific case type before routing to any address other than the one above. `opening_hours` deliberately left unpopulated: genuinely irregular per-office schedules justify JSONB (§6 of DATABASE.md), but populating it needs its own verified source, not guessed at alongside the address. |

### Service types (`service_types`, V17)

`PESEL`, `MELDUNEK`, `DRIVING_LICENCE`, `IMMIGRATION_INFORMATION` — routing/reference
tags only (brief §14), never confused with Phase 4+ `Procedure` content. The one seeded
office is tagged `IMMIGRATION_INFORMATION`.

## 6. Re-verification cadence

No automated re-check job exists yet (that's an `OfficialSource`/`SourceVerification`-style
freshness pipeline, §3/ARCHITECTURE.md §8's "Source freshness" concept — Phase 3 doesn't
build that machinery for reference data, only records dates manually here). Until it
does: re-check this document's DRAFT rows (pre-2000 EU/EEA/EFTA/Schengen accession dates)
before Phase 4 begins consuming country classification in eligibility rules, and re-verify
the one seeded office's address/contact details at least once before any procedure content
routes a user to it.
