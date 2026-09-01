# Procedure Catalogue — Foreigner Warsaw

Status: DRAFT (Phase 0) — revised after Phase 0 quality review
Last updated: 2026-09-01

## How to read this document

Each entry lists: who it applies to, a **jurisdiction tag**, the authority a Warsaw user
actually deals with, whether it is in MVP scope, and a **research status**.

### Jurisdiction tags {#jurisdiction-tags}

This distinction is the whole point of the multi-city architecture (see
[ARCHITECTURE.md §9](../architecture/ARCHITECTURE.md#9-jurisdiction--city-expansion-model)):
a procedure's *legal eligibility rules* and the *office a user is routed to* can sit at
different levels, and conflating them would make city expansion (adding Kraków) require
touching eligibility logic instead of just seeding new geography/office data.

- **NATIONAL** — eligibility conditions come from national immigration law, uniformly
  across Poland. This is true of every third-country and EU-free-movement residence
  procedure below, *even though* a Warsaw applicant's case is physically processed by a
  regional office (Mazowieckie) — that's a routing fact, not a legal one, and is called
  out separately in the "Processing / routing" column.
- **VOIVODESHIP** — the substantive rule itself (not just processing) is set or
  administered at the regional level. None of the currently cataloged procedures need
  this tag for Poland; it exists for completeness and for administrative divisions
  (`Region` rows, [DATABASE.md §2](../database/DATABASE.md#2-geographic--reference-entities))
  where a future country genuinely devolves rule-making regionally.
- **MUNICIPAL** — both the rule and the process are municipal. PESEL, address
  registration (meldunek), and driving-licence exchange are genuinely municipal —
  administered under Warsaw's municipal structures, not the national immigration
  authority.
- **MIXED** — reserved for a procedure whose eligibility conditions themselves differ
  by region (not just its processing office). Not used by anything in this catalogue
  today; kept as a defined value so the schema doesn't need to change if one arises.

No entry here is a substitute for the per-procedure dossier
(`docs/procedures/<category>/<procedure>.md`) required before implementation — see
[ARCHITECTURE.md §12](../architecture/ARCHITECTURE.md#12-documentation-conventions) and
brief §67/§94 (publish validation).

### Research status

- `VERIFIED` — read against the primary official page directly, with a captured
  `content_hash` (DATABASE.md §3), by a human reviewer.
- `DRAFT` — grounded in official-source-citing content found during Phase 0 web
  research, but the primary legal text has not yet been read line-by-line by a
  reviewer. **Still not implementation-ready** — Phase 10 task 10.1 in
  [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) exists specifically to move these to
  `VERIFIED` before any is published to production.
- `NOT_STARTED` — named for catalogue completeness only; no research performed. Must
  not be implemented before it is researched.

---

## A. Residence — third-country nationals

| Procedure | Applies to | Jurisdiction | Processing / routing | MVP | Research status |
|---|---|---|---|---|---|
| Temporary residence and work (uniform permit) | Third-country nationals with a Polish job offer/contract, staying >3 months | NATIONAL | Mazowieckie Voivodeship Office, Dept. for Foreigners' Affairs | **Yes** | DRAFT |
| EU Blue Card (highly qualified employment) | Third-country nationals with a highly-qualified job offer above the salary threshold | NATIONAL | Mazowieckie Voivodeship Office | **Yes** | DRAFT |
| EU Blue Card intra-EU mobility | Blue Card holders from another EU state moving to PL | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Temporary residence for studies | Third-country nationals in full-time studies | NATIONAL | Mazowieckie Voivodeship Office | **Yes** | DRAFT |
| Temporary residence — family reunification (spouse of Polish citizen) | Third-country spouse of a Polish citizen | NATIONAL | Mazowieckie Voivodeship Office | **Yes** | DRAFT |
| Temporary residence — other family reunification (parent/child/dependent) | Third-country family members outside the spouse-of-Polish-citizen case | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Business activity (JDG/company) residence | Third-country nationals running a business in Poland | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Graduate-related stay (post-studies job search / early work) | Recent graduates of Polish universities | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Scientific research / researcher hosting agreement | Researchers under a hosting agreement | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Researcher mobility (EU) | Researchers moving from another EU state | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Internship / traineeship | Trainees on a traineeship agreement | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Volunteering | Volunteers under a hosting entity | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Intra-company transfer (ICT) | Employees transferred within a corporate group | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| ICT mobility (EU) | ICT permit holders moving from another EU state | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Posted worker | Workers posted by a foreign employer | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Seasonal work | Seasonal-work visa/permit holders | NATIONAL | Mazowieckie Voivodeship Office / District Labour Office | No | NOT_STARTED |

## B. EU / EEA / Swiss free movement

| Procedure | Applies to | Jurisdiction | Processing / routing | MVP | Research status |
|---|---|---|---|---|---|
| EU citizen residence registration (>3 months) | EU/EEA/Swiss citizens staying >3 months | NATIONAL | Mazowieckie Voivodeship Office | **Yes** | DRAFT |
| Permanent right of residence certificate (EU citizen) | EU/EEA/Swiss citizens after continuous legal residence | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Residence card of a family member of an EU citizen (third-country national) | Third-country family members of an EU/EEA/Swiss citizen | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Permanent residence card of a family member of an EU citizen | Third-country family members after continuous residence | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| UK Withdrawal Agreement residence cases | UK nationals and family covered by the Withdrawal Agreement | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |

**Note (post-review):** every row in this section is tagged `NATIONAL`, same as Section
A — the EU free-movement rule set and the third-country rule set are both national law
in Poland, and both happen to be processed by the same Mazowieckie office. What
separates B from A is not jurisdiction level but which *legal basis* applies
(Directive 2004/38/EC-derived free movement vs. the Act on Foreigners) — see
[ASSESSMENT_DECISION_TREE.md](ASSESSMENT_DECISION_TREE.md), Step 2, for how the
questionnaire keeps these rule sets from merging.

## C. Permanent / long-term residence

| Procedure | Applies to | Jurisdiction | Processing / routing | MVP | Research status |
|---|---|---|---|---|---|
| Permanent residence permit | Third-country nationals meeting statutory grounds (marriage, Polish origin, refugee status, etc.) | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| EU long-term resident permit | Third-country nationals with 5+ years of continuous legal residence | NATIONAL | Mazowieckie Voivodeship Office | No | NOT_STARTED |
| Karta Polaka related pathway | Holders of the Karta Polaka | NATIONAL | Mazowieckie Voivodeship Office / Consulate | No | NOT_STARTED |

## D. Protection / special status

| Procedure | Applies to | Jurisdiction | Processing / routing | MVP | Research status |
|---|---|---|---|---|---|
| Temporary protection (incl. war-displacement related) | Persons covered by an active temporary protection scheme | NATIONAL | Office for Foreigners / Voivodeship Office | No | NOT_STARTED |
| Refugee status / international protection | Applicants for international protection | NATIONAL | Office for Foreigners | No | NOT_STARTED |
| Subsidiary protection | Applicants for subsidiary protection | NATIONAL | Office for Foreigners | No | NOT_STARTED |
| Humanitarian stay / tolerated stay | Persons granted humanitarian or tolerated stay | NATIONAL | Office for Foreigners / Voivodeship Office | No | NOT_STARTED |

## E. Administrative services (PESEL / registration)

| Procedure | Applies to | Jurisdiction | Processing / routing | MVP | Research status |
|---|---|---|---|---|---|
| PESEL number assignment | Any foreigner needing a Polish national ID number | **MUNICIPAL** | Warsaw district office (e.g. Śródmieście for those without a registerable address) | **Yes** | DRAFT |
| Temporary address registration (zameldowanie na pobyt czasowy) | Foreigners residing at a Warsaw address, deadline varies by citizenship group | **MUNICIPAL** | Warsaw district office | **Yes** | DRAFT |
| Permanent address registration (zameldowanie na pobyt stały) | Foreigners with a stable Warsaw address | **MUNICIPAL** | Warsaw district office | No (post-MVP) | DRAFT |
| Address de-registration (wymeldowanie) | Foreigners leaving a registered address | **MUNICIPAL** | Warsaw district office | No | NOT_STARTED |

## F. Driving

| Procedure | Applies to | Jurisdiction | Processing / routing | MVP | Research status |
|---|---|---|---|---|---|
| Exchange of a foreign driving licence covered by traffic conventions (EU/EEA/Switzerland/Convention states) | Holders of a convention-recognised foreign licence | **MUNICIPAL** | Warsaw district (Administration & Resident Services delegation) | **Yes** | DRAFT |
| Exchange of a foreign driving licence **not** covered by traffic conventions | Holders of a non-convention foreign licence (may require exam) | **MUNICIPAL** | Warsaw district | **Yes** (same procedure family) | DRAFT |

## G. Business

| Procedure | Applies to | Jurisdiction | Processing / routing | MVP | Research status |
|---|---|---|---|---|---|
| Sole proprietorship (JDG) registration for eligible foreigners | Foreigners entitled to run a JDG (varies by status) | NATIONAL | CEIDG / Tax office | No | NOT_STARTED |
| NIP (tax identification) registration | Foreigners needing a tax ID | NATIONAL | Tax office | No | NOT_STARTED |

## H. Other / future

Listed for schema completeness only (brief §20): Trusted Profile, ZUS registration,
NFZ/healthcare enrolment, employment onboarding, marriage/birth registration,
naturalisation, vehicle registration, university administration, accommodation-related
services. All `NOT_STARTED`; jurisdiction tags to be assigned when researched (several
of these — ZUS, NFZ, vehicle registration — are plausibly `NATIONAL` administered
through local branches, similar to Section A, but this must not be assumed without
checking).

---

## MVP set (Phase 10 implementation target)

1. Temporary residence and work
2. EU Blue Card / highly qualified employment
3. Temporary residence for studies
4. Temporary residence — family reunification (spouse of Polish citizen)
5. EU citizen residence registration
6. PESEL number assignment
7. Address registration (meldunek) — temporary registration at minimum
8. Foreign driving licence exchange (convention and non-convention branches)

---

## MVP procedure source records

Full per-field source tracking for each MVP procedure, as required before Phase 10
encoding (`IMPLEMENTATION_PLAN.md` 10.1–10.9). **All statuses below are `DRAFT`** — see
"Research status" above; nothing here is cleared for production until a reviewer reads
the primary page directly.

### 1. Temporary residence and work
- **Official authority**: Urząd do Spraw Cudzoziemców (UDSC) / Mazowieckie Voivodeship
  Office
- **Official URL**: https://mos.cudzoziemcy.gov.pl/en/informacje/czasowy-praca_VN/wymogi_EN
- **Date checked**: 2026-09-01 (secondary-source search pass, not primary-text read)
- **Jurisdiction**: NATIONAL (processed by Mazowieckie Voivodeship Office)
- **Source status**: DRAFT
- **Effective date if known**: minimum-wage figure (PLN 4,806/month) reported effective
  1 Jan 2026; fee figures (440 PLN + 100 PLN) undated in the sources found
- **Notes on uncertainty**: some sources report a shift toward mandatory online
  submission via "MOS 2.0" during 2026 — must confirm exact effective date and scope
  before encoding the "online vs in-person" step data

### 2. EU Blue Card
- **Official authority**: UDSC / Mazowieckie Voivodeship Office; salary threshold set
  via GUS (Statistics Poland) annual announcement
- **Official URL**: https://www.dudkowiak.com/immigration-law-in-poland/eu-blue-card-in-poland/
  (secondary; primary UDSC/MOS Blue Card page not yet directly captured — **action item**
  for 10.1)
- **Date checked**: 2026-09-01
- **Jurisdiction**: NATIONAL (processed by Mazowieckie Voivodeship Office)
- **Source status**: DRAFT
- **Effective date if known**: PLN 13,355.34/month reported effective 1 Jan 2026
  (GUS announcement dated 2026-02-09 per secondary sources — the announcement date
  postdating the claimed effective date is itself a flag to resolve against the primary
  GUS/UDSC publication)
- **Notes on uncertainty**: primary GUS/UDSC publication not yet read directly; the
  announcement-date vs. effective-date discrepancy above must be resolved before this
  becomes `ThresholdVersion.effective_from`

### 3. Temporary residence for studies
- **Official authority**: UDSC / Mazowieckie Voivodeship Office
- **Official URL**: https://www.mos.cudzoziemcy.gov.pl/en/informacje/studia-kurs_EN/wprowadzenie_EN
- **Date checked**: 2026-09-01
- **Jurisdiction**: NATIONAL (processed by Mazowieckie Voivodeship Office)
- **Source status**: DRAFT
- **Effective date if known**: not captured this pass
- **Notes on uncertainty**: permit-duration rules (15 months first year, etc.) need
  verification against the primary text; "sufficient funds" amount, if any specific
  figure exists, not yet found

### 4. Family reunification (spouse of Polish citizen)
- **Official authority**: UDSC / Mazowieckie Voivodeship Office
- **Official URL**: https://www.mos.cudzoziemcy.gov.pl/en/informacje/zwiazek-mal/wprowadzenie_EN
- **Date checked**: 2026-09-01
- **Jurisdiction**: NATIONAL (processed by Mazowieckie Voivodeship Office)
- **Source status**: DRAFT
- **Effective date if known**: not captured this pass
- **Notes on uncertainty**: one secondary source states applications "must" go through
  MOS electronically only — needs primary-source confirmation before being encoded as a
  hard requirement; "stable and regular income sufficient" has no specific figure found
  yet (may need its own `Threshold`, or may be genuinely open-ended/case-by-case, which
  would instead be modeled as a `MORE_INFORMATION_REQUIRED`-producing condition rather
  than a numeric threshold)

### 5. EU citizen residence registration
- **Official authority**: Ministry of the Interior and Administration (MSWiA) / UDSC /
  Mazowieckie Voivodeship Office
- **Official URL**: https://www.gov.pl/web/mswia-en/registration-of-residence
- **Date checked**: 2026-09-01
- **Jurisdiction**: NATIONAL (processed by Mazowieckie Voivodeship Office)
- **Source status**: DRAFT
- **Effective date if known**: not applicable (long-standing EU free-movement rule, no
  recent change identified)
- **Notes on uncertainty**: the "sufficient resources" test for economically inactive
  EU citizens has no specific figure identified in this pass — likely case-by-case, to
  be confirmed

### 6. PESEL number assignment
- **Official authority**: Ministry of Digital Affairs (gov.pl) / Warsaw district office
- **Official URL**: https://www.gov.pl/web/gov/uzyskaj-numer-pesel--usluga-dla-cudzoziemcow-en
  and https://warszawa19115.pl/en/-/assigning-a-pesel-number-at-the-request-of-a-foreigner-who-is-not-a-citizen-of-an-eu-efta-member-state-or-uk-country-or-a-member-of-their-family
- **Date checked**: 2026-09-01
- **Jurisdiction**: MUNICIPAL
- **Source status**: DRAFT
- **Effective date if known**: not applicable (standing procedure)
- **Notes on uncertainty**: which specific Warsaw district office applies for a given
  applicant depends on individual circumstances beyond "no registerable address" — needs
  a fuller routing rule captured from the primary Warszawa 19115 page before encoding
  `ProcedureOffice`

### 7. Meldunek (temporary address registration)
- **Official authority**: Warsaw district office (Warszawa 19115)
- **Official URL**: https://warszawa19115.pl/en/-/zameldowanie-na-pobyt-czasowy-cudzoziemcow-w-tym-obywateli-panstw-czlonkowskich-unii-europejskiej-ue-i-czlonkow-ich-rodzin
- **Date checked**: 2026-09-01
- **Jurisdiction**: MUNICIPAL
- **Source status**: DRAFT
- **Effective date if known**: not applicable (standing procedure)
- **Notes on uncertainty**: exact document list and the online-vs-in-person split for
  EU vs. non-EU applicants needs line-by-line confirmation; the 4-day vs. 30-day
  deadline split is DRAFT-confirmed by two independent secondary mentions but not yet
  read from the primary legal text (Act on the Register of Residents)

### 8. Driving licence exchange
- **Official authority**: Warsaw district Administration & Resident Services
  delegation (Warszawa 19115)
- **Official URL**: convention licences —
  https://warszawa19115.pl/en/-/exchanging-a-foreign-driving-licence-issued-by-a-member-state-of-the-european-union-the-swiss-confederation-efta-a-state-party-to-the-convention-on-road-traffic-or-a-corresponding-model-driving-licence-as-defined-in-these-conventions-for-a-polish-driving-;
  non-convention licences —
  https://warszawa19115.pl/en/-/exchange-of-a-foreign-driving-licence-not-specified-in-the-traffic-conventions-into-a-polish-driving-licence
- **Date checked**: 2026-09-01
- **Jurisdiction**: MUNICIPAL
- **Source status**: DRAFT
- **Effective date if known**: "6 months after establishing residence" validity cutoff —
  not independently dated, needs primary confirmation
- **Notes on uncertainty**: exactly which licence-issuing countries require a
  theoretical/practical exam (the non-convention branch) is a per-country fact
  (`DrivingLicenceRecognitionRule`, DATABASE.md §2) that needs a country-by-country
  research pass, not a single blanket rule — this is the catalogue's most
  country-sensitive procedure and should not be encoded as a single EU-vs-non-EU
  boolean (brief §19 makes the same point explicitly)

---

## Sources consulted this pass

- [migracja.gov.pl / MOS — Temporary residence and work permit](https://mos.cudzoziemcy.gov.pl/en/informacje/czasowy-praca_VN/wymogi_EN)
- [UDSC — Temporary residence and work permit](https://archiwalna.udsc.gov.pl/en/cudzoziemcy/obywatele-panstw-trzecich/chce-przedluzyc-swoj-pobyt-w-polsce/zezwolenie-na-pobyt-czasowy/praca/jednolite-zezwolenie-na-pobyt-i-prace/)
- [MOS — Temporary residence permit for studies](https://www.mos.cudzoziemcy.gov.pl/en/informacje/studia-kurs_EN/wprowadzenie_EN)
- [MOS — Family reunification (marriage)](https://www.mos.cudzoziemcy.gov.pl/en/informacje/zwiazek-mal/wprowadzenie_EN)
- [MOS — Registration of residence of an EU citizen](https://www.mos.cudzoziemcy.gov.pl/en/categories-information/possibilities-legalization/i-hold-the-citizenship-of-the-eu-no-li-is-ch-and-their-family-members/temporary-stay-90-days/registration-of-residence-of-an-eu-citizen/)
- [gov.pl — Registration of residence (MSWiA)](https://www.gov.pl/web/mswia-en/registration-of-residence)
- [gov.pl — Get a PESEL ID, service for foreigners](https://www.gov.pl/web/gov/uzyskaj-numer-pesel--usluga-dla-cudzoziemcow-en)
- [Warszawa 19115 — PESEL for non-EU/EFTA/UK foreigners](https://warszawa19115.pl/en/-/assigning-a-pesel-number-at-the-request-of-a-foreigner-who-is-not-a-citizen-of-an-eu-efta-member-state-or-uk-country-or-a-member-of-their-family)
- [Warszawa 19115 — Temporary residence registration of foreigners](https://warszawa19115.pl/en/-/zameldowanie-na-pobyt-czasowy-cudzoziemcow-w-tym-obywateli-panstw-czlonkowskich-unii-europejskiej-ue-i-czlonkow-ich-rodzin)
- [Warszawa 19115 — Exchange of a driving licence covered by traffic conventions](https://warszawa19115.pl/en/-/exchanging-a-foreign-driving-licence-issued-by-a-member-state-of-the-european-union-the-swiss-confederation-efta-a-state-party-to-the-convention-on-road-traffic-or-a-corresponding-model-driving-licence-as-defined-in-these-conventions-for-a-polish-driving-)
- [Warszawa 19115 — Exchange of a driving licence not covered by traffic conventions](https://warszawa19115.pl/en/-/exchange-of-a-foreign-driving-licence-not-specified-in-the-traffic-conventions-into-a-polish-driving-licence)
- [Mazowieckie Voivodeship Office — Wydział Spraw Cudzoziemców contact](https://www.gov.pl/web/uw-mazowiecki/dane-kontaktowe)

**None of these have `VERIFIED` status.** They are secondary confirmations that
official pages exist and say roughly what aggregator/law-firm sites report. Phase 10
task 10.1 in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) is the gate: a reviewer
opens each primary page directly, captures a `content_hash`, and only then flips status
to `VERIFIED` — no MVP procedure publishes to production before that happens.
