# Official Source Register — Phase 10

Status: IN PROGRESS. Tracks every `OfficialSource` this pass identifies as a citation
candidate, its Tier, and its verification status **in this document** (a plain-language
mirror of the `OfficialSource`/`SourceVerification` rows that will actually be created
through the real Admin UI — see [ADMIN_WORKFLOW](#admin-workflow-conformance) below).
"VERIFIED" here means a human reviewer has read the primary page directly and recorded
that outcome through the real `AdminSourceController` verification workflow (hardened
this session — see the `fix(admin): harden legal content governance...` commit); nothing
in this register is self-verified by the research pass that found it, per CLAUDE.md.

Tier definitions (per CLAUDE.md's sourcing priority order):
- **Tier 1**: Polish legislation/ISAP, gov.pl, UDSC/MOS, Mazowieckie Voivodeship Office,
  Warszawa 19115 / Warsaw municipal gov pages, official application forms.
- **Tier 2**: other official (non-Warsaw/non-Mazowieckie) Polish government sites — e.g.
  another Voivodeship Office's own BIP page, an archived ministry page still hosted on a
  `.gov.pl`-family domain.
- Never cited as legal basis: law firms, blogs, aggregators, forums — used only to
  *locate* candidate primary URLs, per CLAUDE.md.

| # | Title | URL | Authority | Tier | Reachability this pass | Verification status |
|---|---|---|---|---|---|---|
| 1 | Get a PESEL ID — service for foreigners | https://www.gov.pl/web/gov/uzyskaj-numer-pesel--usluga-dla-cudzoziemcow-en | Ministry of Digital Affairs (gov.pl) | 1 | Reached | NOT_VERIFIED (research-pass read only) |
| 2 | Assigning a PESEL number — foreigner who is **not** an EU/EFTA/UK citizen | https://warszawa19115.pl/en/-/nadanie-numeru-pesel-na-wniosek-cudzoziemca-ktory-nie-jest-obywatelem-krajow-ue-efta-wielkiej-brytanii-lub-czlonkiem-ich-rodzin | City of Warsaw (Warszawa 19115) | 1 | Reached | NOT_VERIFIED |
| 3 | Assigning a PESEL number — foreigner who **is** an EU/EFTA/UK citizen (or family member) | https://warszawa19115.pl/en/-/assigning-a-pesel-number-at-the-request-of-a-foreigner-who-is-not-a-citizen-of-an-eu-efta-member-state-or-uk-country-or-a-member-of-their-family | City of Warsaw (Warszawa 19115) | 1 | Reached — **but URL slug and rendered content disagree on which group this page is for; see PHASE_10_RESEARCH_LOG.md §1**. Do not create the `OfficialSource` row against this URL until a human confirms which content it actually serves. | BLOCKED — do not verify until the URL/content mismatch is resolved |
| 4 | Zameldowanie na pobyt czasowy cudzoziemców (temporary residence registration of foreigners) | https://warszawa19115.pl/en/-/zameldowanie-na-pobyt-czasowy-cudzoziemcow-w-tym-obywateli-panstw-czlonkowskich-unii-europejskiej-ue-i-czlonkow-ich-rodzin | City of Warsaw (Warszawa 19115) | 1 | Reached | NOT_VERIFIED |
| 5 | Zameldowanie na pobyt czasowy cudzoziemców (archived departmental explainer, quotes the statute) | https://archiwum.mswia.gov.pl/pl/sprawy-obywatelskie/obowiazek-meldunkowy/12986,Zameldowanie-na-pobyt-czasowy-cudzoziemcow.html | Ministry of Interior and Administration (archived) | 1 | Reached | NOT_VERIFIED |
| 6 | Registration of residence (EU citizens) | https://www.gov.pl/web/mswia-en/registration-of-residence | Ministry of Interior and Administration (gov.pl) | 1 | Reached | NOT_VERIFIED |
| 7 | Registration of residence of an EU citizen (MOS) | https://www.mos.cudzoziemcy.gov.pl/en/categories-information/possibilities-legalization/i-hold-the-citizenship-of-the-eu-no-li-is-ch-and-their-family-members/temporary-stay-90-days/registration-of-residence-of-an-eu-citizen/ | UDSC (Moduł Obsługi Spraw) | 1 | **Not reached — TLS certificate error from this environment on every attempt** | NOT ATTEMPTED (blocked by access, not content) |
| 8 | Zezwolenie na pobyt czasowy w celu wykonywania pracy (temporary residence + work permit) | https://migrant.wsc.mazowieckie.pl/pl/procedury/zezwolenie-na-pobyt-czasowy-w-celu-wykonywania-pracy | **Mazowieckie Voivodeship Office** (the actual competent processing authority for Warsaw) | 1 | Reached — highest-authority source reached this pass | NOT_VERIFIED |
| 9 | MOS — Zezwolenie na pobyt czasowy i pracę (wymogi) | https://mos.cudzoziemcy.gov.pl/en/informacje/czasowy-praca_VN/wymogi_EN | UDSC (Moduł Obsługi Spraw) | 1 | **Not reached — TLS certificate error** | NOT ATTEMPTED |
| 10 | MOS — Zezwolenie na pobyt czasowy i pracę (opłata) | https://mos.cudzoziemcy.gov.pl/informacje/czasowy-praca/oplata | UDSC (Moduł Obsługi Spraw) | 1 | **Not reached — TLS certificate error** | NOT ATTEMPTED |
| 11 | Zezwolenie na pobyt czasowy - studia | https://bip.lubuskie.uw.gov.pl/sprawy_obywatelskie/Zezwolenie_na_pobyt_czasowy_-_studia | Lubuskie Voivodeship Office (same-Tier stand-in; **not** the Mazowieckie office) | 1 | Reached | NOT_VERIFIED — additionally needs a Mazowieckie-specific replacement/companion before production use, per PHASE_10_RESEARCH_LOG.md §5 |
| 12 | MOS — Zezwolenie na pobyt czasowy w celu kształcenia się na studiach (przepisy) | https://www.mos.cudzoziemcy.gov.pl/informacje/studia-kurs/przepisy | UDSC (Moduł Obsługi Spraw) | 1 | **Not reached — TLS certificate error** | NOT ATTEMPTED |
| 13 | Ustawa o cudzoziemcach (Act of 12 Dec 2013 on Foreigners), Art. 149 / Chapter 6 Section V | isap.sejm.gov.pl (exact PDF URL located by search, not opened directly this pass) | Sejm (ISAP) | 1 | **Located by search only, not fetched directly this pass** | NOT ATTEMPTED — highest priority for a human follow-up read, since it's the actual statute |

## Sources consulted for context/corroboration only (never cited as legal basis)

These appeared during search and were used only to find candidate primary URLs above,
or to sanity-check a figure pending primary confirmation (each such use is flagged
inline in `PHASE_10_RESEARCH_LOG.md`). None of these are, or will become, an
`OfficialSource` row.

- migrant.poznan.uw.gov.pl (a different Voivodeship's own glossary page — technically
  Tier 2 official, used only as corroboration since the Mazowieckie-specific version
  wasn't reachable; a human reviewer should decide whether to formally register this as
  a Tier-2 `OfficialSource` for the EU-registration certificate-validity fact, or wait
  for the Mazowieckie/MOS page to become reachable)
- Assorted law-firm/relocation-agency pages (cgomobility.pl, dudkowiak.com,
  wizaserwis.pl, jakiwniosek.pl, infoopt.pl, prawoiadministracja.pl) — search-discovery
  only, never cited as a legal basis anywhere in the research log.
- lexlege.pl / lexlege.pb.pl — a statute-text aggregator (reproduces the Act's text);
  useful for locating the right chapter/article number, but the *citation* target
  remains ISAP directly (source #13 above), not this aggregator.

## Access limitations encountered this pass

Every `mos.cudzoziemcy.gov.pl` URL attempted (in any subdomain/path combination) failed
with `unable to verify the first certificate` from this environment's fetch tool. This
affected sources #7, #9, #10, and #12 above — all on the same domain — strongly
suggesting an environment-specific TLS trust-store gap for that one host rather than a
per-page issue. **A human reviewer with normal browser access should retry these
directly** before concluding anything about their content; this pass could not read them
at all, favourably or unfavourably.

## Admin workflow conformance

No `OfficialSource`, `Procedure`, `Rule`, `Threshold`, or their versions have been
created in the database from this register yet as of the point this file was written.
When they are, they will be created exclusively through the real Admin UI/API (the
hardened `/api/v1/admin/**` and retrofitted `/api/v1/internal/content/**` endpoints),
each write producing a real `AuditLog` entry, per the Phase 10 brief and the
pre-Phase-10 hardening checkpoint completed this session. See `MVP_CONTENT_COVERAGE.md`
for the live status of that authoring work.
