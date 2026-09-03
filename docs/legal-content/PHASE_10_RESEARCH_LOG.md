# Phase 10 Research Log — Verified Warsaw MVP Legal Content

Status: IN PROGRESS
Research pass performed: 2026-09-03
Researcher: Claude (Sonnet 5), under human supervision — see CLAUDE.md "never fabricate a
legal/procedural fact" and "never let an LLM decide eligibility." This log records what
was *found and where*; a human legal reviewer must still read the primary text before
any of this is marked `VERIFIED` in the database (see `OFFICIAL_SOURCE_REGISTER.md`).

Scope: the five procedures named in the Phase 10 brief — **PESEL, Meldunek (temporary
address registration), EU Citizen Residence Registration, Temporary Residence and Work,
Temporary Residence for Studies**. EU Blue Card, Family Reunification, and Foreign
Driving Licence Exchange are explicitly out of scope for this pass (fast-follow) even
though `PROCEDURE_CATALOGUE.md` already has DRAFT notes for them — those notes are left
untouched.

This log supersedes, for these five procedures only, the "MVP procedure source records"
section of `docs/product/PROCEDURE_CATALOGUE.md` (which remains the source of truth for
the other MVP-catalogued but not-yet-researched procedures). `PROCEDURE_CATALOGUE.md` is
not rewritten in this pass; it should be updated to point here in a follow-up.

Method: primary/official pages fetched directly (WebFetch) wherever reachable; where a
government TLS endpoint was unreachable from this environment, a secondary search
(WebSearch) was used to identify the right primary citation and, where possible, a
different reachable official mirror (e.g. a Voivodeship Office's own BIP page, or an
archived MSWiA page) was fetched directly instead. Every fact below is tagged with which
of these applied. No blog/law-firm/forum content is cited as a legal basis anywhere in
this document — those sources, where they appeared in search results, were used only to
locate candidate primary URLs, per CLAUDE.md.

---

## 1. PESEL number assignment

**Jurisdiction**: MUNICIPAL (Warsaw district Office for Administration and Civic
Affairs — Biuro Administracji i Spraw Obywatelskich, "BASO").

### Sources fetched directly (primary/official)
- gov.pl — "Get a PESEL ID, service for foreigners"
  https://www.gov.pl/web/gov/uzyskaj-numer-pesel--usluga-dla-cudzoziemcow-en — fetched
  successfully.
- Warszawa 19115 — the non-EU/EFTA/UK-specific procedure page (see discrepancy note
  below for why this is a *different* URL than the one recorded in
  `PROCEDURE_CATALOGUE.md`):
  https://warszawa19115.pl/en/-/nadanie-numeru-pesel-na-wniosek-cudzoziemca-ktory-nie-jest-obywatelem-krajow-ue-efta-wielkiej-brytanii-lub-czlonkiem-ich-rodzin
  — fetched successfully.
- Warszawa 19115 — the EU/EFTA/UK-citizen procedure page (fetched to confirm the
  discrepancy, ended up being the more useful source for the *general* required-document
  list, which is materially the same for both groups minus the in-person requirement):
  https://warszawa19115.pl/en/-/assigning-a-pesel-number-at-the-request-of-a-foreigner-who-is-not-a-citizen-of-an-eu-efta-member-state-or-uk-country-or-a-member-of-their-family
  — fetched successfully, **but see discrepancy below**.

### ⚠️ Discrepancy found (logged for `OPEN_LEGAL_QUESTIONS.md`)
The URL recorded in `PROCEDURE_CATALOGUE.md` for "PESEL for non-EU/EFTA/UK foreigners"
(`.../assigning-a-pesel-number-at-the-request-of-a-foreigner-who-is-not-a-citizen-of-an-eu-efta-member-state-or-uk-country-or-a-member-of-their-family`)
in fact renders content for the *opposite* group — its own body text opens "You can
apply for a PESEL number in person or by proxy if you are: a citizen of an EU Member
State ... EFTA ... Switzerland ... United Kingdom." This looks like a CMS routing/slug
bug on the Warszawa 19115 site itself (URL and rendered content disagree), not a
research error on our part — re-fetched twice independently with the same result. The
*correct* non-EU/EFTA/UK page was located via search under a different, Polish-titled
slug that nonetheless serves the `/en/` path:
`.../nadanie-numeru-pesel-na-wniosek-cudzoziemca-ktory-nie-jest-obywatelem-krajow-ue-efta-wielkiej-brytanii-lub-czlonkiem-ich-rodzin`.
**Action for a human reviewer**: re-check both URLs directly in a browser before
`OfficialSource` records are created, in case the site has since fixed its own routing;
cite whichever URL actually serves the third-country-national content at verification
time.

### Facts found
- **Eligibility / who needs to actively apply**: a foreigner registering an address
  (meldunek) is assigned a PESEL automatically as part of that process; a foreigner who
  cannot register an address (no registerable address) must apply for PESEL directly at
  a municipal office. (gov.pl)
- **Required documents** (both citizen groups; the third-country page's list is a
  subset/match of the general list):
  1. Completed & hand-signed PESEL application form (downloadable PDF)
  2. Identity document (e.g. passport) presented for inspection
  3. Supporting documents for the data entered in application sections/items 3–5
  4. Power of attorney, if applying by proxy (must be certified as a true copy — by
     notary, advocate, legal adviser, or an authorized office employee — if not
     presented as the signed original)
- **In-person requirement — a materially important, dated change**: as of **1 January
  2026**, a third-country national (non-EU/EFTA/Swiss/UK) may submit the PESEL
  application **only in person** — representation by proxy or postal submission is no
  longer accepted for this group. Quoted directly from the Warszawa 19115 page: *"Od 1
  stycznia 2026 r. wniosek możesz złożyć tylko osobiście."* This is already in force as
  of this research pass (2026-09-03). EU/EFTA/UK citizens and their family members may
  still apply by proxy.
- **Where**: any delegation ("delegatura") of the Warsaw Office for Administration and
  Civic Affairs (BASO); appointment booking available via the city's online reservation
  system.
- **Fee**: none — "free of charge" (gov.pl); confirmed independently by the Warszawa
  19115 page.
- **Processing time**: no fixed statutory number of days found in either source; both
  describe the office acting "promptly"/"without delay" ("niezwłocznie") and, if
  approved, issuing a written notification of the assigned number on the spot or shortly
  after.
- **Legal basis**: Act of 24 September 2010 on Population Registration ("ustawa o
  ewidencji ludności", Journal of Laws 2021 item 510 per gov.pl's citation) and the
  Regulation of the Minister of Internal Affairs of 4 January 2012 on PESEL assignment
  procedure.

### Not yet resolved (see OPEN_LEGAL_QUESTIONS.md)
- The exact list of "documents confirming data in items 3–5" (i.e. what those specific
  application fields are, and what counts as acceptable proof) was not spelled out in
  either source at the level of detail needed for a `DocumentRequirement` row — the
  downloadable PDF application form itself would need to be read to enumerate this
  precisely.
- Which specific Warsaw district/delegation a given applicant is routed to (routing
  logic beyond "any delegation, book an appointment") — `PROCEDURE_CATALOGUE.md` already
  flagged this as unresolved; this pass did not resolve it further.

---

## 2. Meldunek — temporary address registration (zameldowanie na pobyt czasowy)

**Jurisdiction**: MUNICIPAL (Warsaw district Office for Administration and Civic
Affairs).

### Sources fetched directly (primary/official)
- Warszawa 19115 — "Temporary residence registration of foreigners..."
  https://warszawa19115.pl/en/-/zameldowanie-na-pobyt-czasowy-cudzoziemcow-w-tym-obywateli-panstw-czlonkowskich-unii-europejskiej-ue-i-czlonkow-ich-rodzin
  — fetched successfully.
- Archived MSWiA (Ministry of Interior and Administration) page — "Zameldowanie na
  pobyt czasowy cudzoziemców":
  https://archiwum.mswia.gov.pl/pl/sprawy-obywatelskie/obowiazek-meldunkowy/12986,Zameldowanie-na-pobyt-czasowy-cudzoziemcow.html
  — fetched successfully, and used specifically to resolve the exemption-threshold
  discrepancy below with verbatim statutory-paraphrase quotes.

### Facts found
- **Deadline to register, by citizenship group** (confirmed with verbatim Polish
  quotes from the archived MSWiA page):
  - EU/EFTA/Swiss citizens and their family members: **within 30 days** of arrival —
    *"jest obowiązany zameldować się w miejscu pobytu czasowego najpóźniej w 30 dniu,
    licząc od dnia przybycia."*
  - All other (third-country) foreigners: **within 4 days** of arrival — *"jest
    obowiązany zameldować się w miejscu pobytu czasowego najpóźniej czwartego dnia,
    licząc od dnia przybycia."*
- **Exemption threshold — differs by group, and this is the discrepancy the initial
  DRAFT note flagged as needing primary-text confirmation, now resolved**:
  - EU/EFTA/Swiss citizens and family members: **no registration obligation if the stay
    does not exceed 3 months** — *"Jeżeli jego pobyt nie przekracza 3 miesięcy nie ma
    obowiązku zameldowania się."*
  - Other (third-country) foreigners: **no registration obligation if the stay does not
    exceed 30 days** — *"chyba że jego pobyt na terytorium Rzeczypospolitej Polskiej nie
    przekracza 30 dni."*
  These are two different thresholds for two different groups, not one shared 30-day
  rule — a genuinely easy point to get wrong, and exactly the kind of fact that must be
  encoded as a `CountrySpecificRule`/group-conditioned `Rule`, not a single boolean.
- **Required documents** (Warszawa 19115):
  1. Completed temporary-residence registration form (a separate form per person,
     including children)
  2. Valid travel document
  3. Proof of legal residence status, which varies by citizenship: passport/ID for
     EU/EEA/Swiss; visa for non-EU visa holders; valid certificate of residence
     registration (or proof of permanent residence rights) for UK citizens; a residence
     card or family-relationship documents for family members
  4. Proof of a right to occupy the dwelling: lease, property deed/ownership document,
     or an administrative decision confirming occupancy rights
  5. Written consent of the landlord/owner (if the applicant is not the owner),
     confirmed on the form itself (item 6)
- **Submission method**: in person at any district delegation, or online via gov.pl
  using a Trusted Profile / qualified e-signature / e-ID — but **online submission is
  not available to non-EU/EFTA/Swiss citizens**, who must present their travel/residence
  documents in person. Online registration for an eligible applicant is described as
  automatic when the address matches the land registry.
- **Fee**: registration itself is free; an optional confirmation certificate costs PLN
  17 stamp duty if requested.
- **Processing time**: "niezwłocznie" (without delay) — no fixed number of days found.
- **No administrative appeal available** for this matter, per the Warszawa 19115 page:
  *"W tej sprawie nie możesz się odwołać."*
- **Legal basis**: Act of 24 September 2010 on Population Registration; Regulation of
  the Minister of Internal Affairs and Administration of 13 December 2017 on
  registration forms and procedures.

### Not yet resolved
- The exact wording/enumeration of acceptable "proof of legal residence status"
  documents per every possible applicant sub-category (e.g. exact list of what counts
  as a UK-citizen "certificate of residence registration") needs a line-by-line read of
  the primary text before encoding as separate `DocumentRequirement` rows per
  applicant-category branch.

---

## 3. EU Citizen Residence Registration (>3 months)

**Jurisdiction**: NATIONAL (EU free-movement law; processed by the Mazowieckie
Voivodeship Office for a Warsaw applicant).

### Sources
- gov.pl (MSWiA English) — "Registration of residence"
  https://www.gov.pl/web/mswia-en/registration-of-residence — fetched successfully.
- MOS (Moduł Obsługi Spraw) EU-citizen registration page — attempted twice
  (`mos.cudzoziemcy.gov.pl` and `www.mos.cudzoziemcy.gov.pl` variants); **both fetches
  failed with a TLS certificate error from this environment** ("unable to verify the
  first certificate") rather than returning content — this is an access/environment
  limitation, not a finding that the page doesn't exist or disagrees with anything.
  Flagged in `OPEN_LEGAL_QUESTIONS.md` for a human reviewer with normal browser access
  to fetch directly.
- Secondary corroboration (search only, not cited as legal basis) on the 10-year
  document-validity point: a Wojewoda regional office page
  (`migrant.poznan.uw.gov.pl`) independently describes the same "registration is
  indefinite; the certificate document itself is reissued every 10 years" split found on
  gov.pl, which is reassuring corroboration but not a substitute for reading the
  Mazowieckie Office's own page once it's reachable.

### Facts found
- **Deadline**: registration must happen within 3 months of arrival — the gov.pl page's
  exact framing is "no later than the next day after the end of the 3-month period."
- **Exemption / jobseeker extension**: an EU/EEA/Swiss citizen who entered Poland to
  seek employment may remain up to 6 months without registering, provided they are
  "actively continuing to seek employment" and have "a genuine chance of being engaged."
- **Eligibility categories and their required documents** (gov.pl):
  1. **Employee**: employment contract, a work certificate, or the employer's written
     declaration of intent to employ.
  2. **Self-employed**: proof of entry in the National Court Register (KRS) or the
     Central Registration and Information on Business (CEIDG).
  3. **Student / vocational trainee**: admission certificate from the educational
     institution; health insurance (public Polish insurance, an EU coordination
     provision, or private insurance covering all costs); a written statement of
     sufficient resources not to become a burden on the social assistance system (no
     specific figure given on this page — flagged as open in `PROCEDURE_CATALOGUE.md`
     and still open here).
  4. **Economically inactive**: the same health-insurance options as students, plus
     proof of financial resources exceeding the thresholds used for social-assistance
     eligibility (again no single figure stated on this page).
  5. **Family member**: marriage certificate / documents proving the family
     relationship, plus age/dependency documentation where relevant.
- **Universal documents**: completed application form; four biometric photographs (each
  taken within the last 6 months); a valid travel or identity document (original,
  presented for inspection); the EU citizen's own residence registration certificate, if
  the applicant is registering as that citizen's family member.
- **Where**: the Voivodeship Office (Wojewoda) for the applicant's place of residence —
  for Warsaw, the Mazowieckie Voivodeship Office.
- **Fee**: none.
- **Processing time**: "without undue delay" — no fixed number of days found.
- **Document validity**: the registration itself is indefinite/permanent; the physical
  certificate document is issued with a 10-year validity and must be reissued/exchanged
  after that period (gov.pl states "10 years"; corroborated independently by a separate
  Wojewoda office's own glossary page, though not the Mazowieckie office's own text,
  which could not be reached this pass).
- **Consequence of non-compliance**: the gov.pl page states non-registration is subject
  to a fine, without giving a specific amount.

### Not yet resolved
- No specific PLN figure for the "sufficient resources" test for students / economically
  inactive applicants was found in any source reached this pass — `PROCEDURE_CATALOGUE.md`
  already flagged this as likely case-by-case rather than a single number; this pass did
  not find evidence either way and it remains open.
- The Mazowieckie Voivodeship Office's own procedure page for this specific service
  (as opposed to the national MSWiA/gov.pl summary) was not successfully reached this
  pass due to the TLS error above — a human reviewer with normal browser access should
  fetch it directly before this is marked `VERIFIED`, since it is the actual processing
  authority for Warsaw applicants and may carry Warsaw-specific detail (e.g. which
  Mazowieckie Office building/queue) not present on the national summary page.

---

## 4. Temporary Residence and Work (uniform/single permit)

**Jurisdiction**: NATIONAL (Act on Foreigners; processed by the Mazowieckie Voivodeship
Office for a Warsaw applicant).

### Sources fetched directly (primary/official)
- **migrant.wsc.mazowieckie.pl** — the Mazowieckie Voivodeship Office's own procedure
  page for this exact permit:
  https://migrant.wsc.mazowieckie.pl/pl/procedury/zezwolenie-na-pobyt-czasowy-w-celu-wykonywania-pracy
  — fetched successfully. **This is the single most authoritative source reached this
  pass** — it's the actual competent regional office's own current procedure text, not a
  national summary or a secondary aggregator.
- `mos.cudzoziemcy.gov.pl` (both the English-language wymogi page and the
  `/oplata` fee page) — both attempts failed with the same TLS certificate error as
  above; not a content finding, an access limitation.
- Search-only corroboration (not cited as legal basis) of the fee tiers and the 60-day
  statutory processing clock, from a mix of other Wojewoda BIP pages and legal-services
  aggregator commentary — used only to sanity-check the primary migrant.wsc.mazowieckie.pl
  figures, not as the citation itself.

### Facts found
- **Purpose/duration test**: applicant must intend to stay in Poland longer than 3
  months, with work as the primary purpose of residence.
- **Minimum salary condition**: the monthly salary specified in the work conditions must
  meet or exceed the statutory minimum wage, **currently PLN 4,806 gross/month**,
  irrespective of working hours or contract type. This matches the figure already
  recorded as DRAFT in `PROCEDURE_CATALOGUE.md`, now corroborated directly from the
  Mazowieckie Office's own page rather than only a secondary source.
- **Visa-type exclusion**: an application is rejected outright if the applicant is
  currently on a Schengen or national visa issued for a purpose incompatible with work
  (tourism, family visits, sport, culture, conferences, study, vocational training,
  exchange programmes, humanitarian programmes, or holiday work).
- **Required documents/attachments** (via the mandatory MOS online portal — see below):
  - A colour photograph meeting a specific digital spec (684×883 px, ≤2.5 MB, taken
    within the last 6 months)
  - Scans of all pages of a valid travel document
  - Proof of payment of the statutory permit fee (see fee tiers below)
  - Proof of payment of the PLN 100 residence-card fee
  - Current health insurance confirmation
  - "Attachment No. 1" — a form the *employer* completes, confirming the work
    conditions offered
  - Proof of qualifications, for a regulated profession, where applicable
  - Employer financial documentation demonstrating capacity to meet the wage obligation
- **Fee tiers**: PLN 340 / 440 / 640 depending on permit category (e.g. the lower PLN
  340 figure applies to a board-member-type case per secondary corroboration — the
  primary Mazowieckie page lists the three figures but this pass did not fully resolve
  which category maps to which of the three amounts down to the line-item level; PLN 440
  is the figure already recorded in `PROCEDURE_CATALOGUE.md` and is very likely the
  standard-employee tier given the secondary corroboration, but this should be confirmed
  against Attachment No. 4 to the Act, not assumed). Residence card fee: PLN 100
  separately, universal.
- **Mandatory online submission — a materially important, dated rule change**: as of
  **27 April 2026**, this application must be submitted exclusively through the MOS
  ("Moduł Obsługi Spraw") online portal — the Mazowieckie page cites the legal basis for
  this as Foreigners Act articles **106c, 106k, 106l, 203c, and 219c**. This is already
  in force as of this research pass.
- **Processing time**: no statutory number of days was stated on the Mazowieckie page
  itself; search-only corroboration (not a primary citation) suggests a 60-day statutory
  clock exists elsewhere in the Act but that actual processing routinely takes several
  months to over a year in practice — if a `ProcedureStep`/expectation-setting field cites
  a number, it must cite the statutory 60-day figure from the Act text directly (not yet
  independently confirmed against ISAP this pass) and separately warn that real-world
  timelines are longer, rather than presenting 60 days as a promise.

### Not yet resolved
- Exact fee-tier-to-applicant-category mapping (340 / 440 / 640 PLN) needs to be
  confirmed against the Act's own fee schedule/regulation, not inferred from secondary
  commentary.
- The statutory 60-day processing-time figure needs independent confirmation directly
  against the Act on Foreigners text (ISAP) before being encoded as a `Threshold` or
  step-level expectation.
- `mos.cudzoziemcy.gov.pl` itself (the national online-portal informational site) could
  not be reached this pass due to a TLS error in this environment — should be retried by
  a human reviewer with normal browser access, since it's the actual system applicants
  submit through post-27-April-2026.

---

## 5. Temporary Residence for Studies

**Jurisdiction**: NATIONAL (Act on Foreigners, Chapter 6, Section V; processed by the
Mazowieckie Voivodeship Office for a Warsaw applicant).

### Sources
- Lubuskie Voivodeship Office BIP — "Zezwolenie na pobyt czasowy - studia"
  https://bip.lubuskie.uw.gov.pl/sprawy_obywatelskie/Zezwolenie_na_pobyt_czasowy_-_studia
  — fetched successfully. A different region's Voivodeship Office than Mazowieckie, but
  since the underlying rule is NATIONAL (only the processing office differs by
  Voivodeship for this category, not the substantive eligibility rule), this is used
  here as a same-Tier official-source stand-in while the Mazowieckie-specific page
  remains unreached; **a human reviewer should still confirm the Mazowieckie Office's
  own version of this page for any Warsaw-specific procedural detail** before this is
  `VERIFIED`.
- `mos.cudzoziemcy.gov.pl` studies page — failed with the same TLS error pattern as
  above.
- Search-only corroboration for the legal-basis chapter/article number (Art. 149 of the
  Act of 12 December 2013 on Foreigners) — pointed at ISAP
  (`isap.sejm.gov.pl`) as the actual statute text; the ISAP PDF itself was located by
  search but not opened directly this pass (time-boxed) — flagged in
  `OPEN_LEGAL_QUESTIONS.md` for direct ISAP confirmation.

### Facts found
- **Eligibility**: the main purpose of the stay must be undertaking or continuing
  studies at an approved institution, and studies must justify a stay of more than 3
  months.
- **Required documents**: valid passport (all pages); a current digital photograph; a
  birth certificate with a sworn Polish translation; proof of paid tuition (if the
  programme is fee-based); health insurance documentation (or proof of cost coverage);
  financial documentation proving sufficient funds (see below); proof of payment of both
  fees; a written, electronically-signed attachment/confirmation from the educational
  institution.
- **Fees**: PLN 100 residence card fee; PLN 340 stamp duty for the permit itself
  (explicitly non-refundable if the application is denied).
- **Permit duration** — a materially important rule this pass resolved from DRAFT to a
  specific, sourced structure:
  - First permit, first-year student: **15 months**
  - First permit, EU mobility programme participant: **2 years**
  - Subsequent permits: duration of the study programme **plus 3 months**, capped at a
    **maximum of 3 years**
  - Short studies (under 1 year): the academic year's duration **plus 3 months**
  - The 15-month first-year figure is independently corroborated by search-located
    references to Article 149 of the Act of 12 December 2013 on Foreigners (Chapter 6,
    Section V) — the chapter/article citation itself was located via search, not opened
    directly against ISAP this pass (see "Not yet resolved" below).
- **"Sufficient funds" requirement** — **this is a different test than the flat
  border-crossing minimum-funds figures (PLN 200/500/2500) that an initial, broader
  search surfaced** for entry generally; those figures were not corroborated as
  specifically applying to *this* permit's "sufficient funds" test on the Lubuskie page
  and should not be encoded as this procedure's `Threshold` without further
  confirmation. What the Lubuskie page actually states for this specific permit is a
  test pegged to the social-assistance income thresholds: a monthly amount higher than
  the income level qualifying for social assistance benefits — approximately **PLN 823**
  for an applicant with family members, or **PLN 1,010** for a solo applicant, **after**
  deducting housing costs. These PLN 823/1,010 figures are themselves derived from a
  separate social-assistance regulation, not the Act on Foreigners directly, and should
  be confirmed against that regulation's current (2026) value before being encoded as a
  `Threshold` — social-assistance thresholds are indexed and change periodically.
  Acceptable proof includes: traveler's cheques, a bank-issued credit-limit statement,
  a bank account statement (issued no more than 1 month before the application),
  scholarship confirmation, or an employer's salary statement.
- **Application deadline**: submit via the MOS portal no later than the last day of the
  applicant's current legal stay.
- **Legal basis**: Act of 12 December 2013 on Foreigners (Chapter 6, Section V —
  "Zezwolenie na pobyt czasowy w celu kształcenia się na studiach. Mobilność studenta");
  Act of 16 November 2016 on Stamp Duty; and two 2025/2026-dated ministerial regulations
  referenced by the Lubuskie page (25 November 2025 and 10 April 2026) whose exact
  subject matter was not fully resolved this pass — likely the application-form
  regulation and/or a fee regulation, needs confirmation.

### Not yet resolved
- The two ministerial regulations dated 25 November 2025 and 10 April 2026 referenced
  by the Lubuskie page need their exact subject matter and ISAP citation confirmed.
- Article 149's exact text was not opened directly against ISAP this pass (only located
  via search) — should be read directly before `VERIFIED`.
- The PLN 823/1,010 sufficient-funds figures' source regulation and its 2026 currency
  need confirming — these are indexed social-assistance figures, not fixed in the Act
  itself, and are exactly the kind of number that must live in `Threshold`/`ThresholdVersion`
  with its own sourced `effective_from`, not be hard-coded.
- The Mazowieckie Voivodeship Office's own version of this procedure page (as opposed to
  Lubuskie's) was not reached this pass.

---

## Summary: research completeness by procedure

| Procedure | Primary/official sources reached | Materially important facts newly resolved this pass | Still open |
|---|---|---|---|
| PESEL | 3 (gov.pl, 2× Warszawa 19115) | Mandatory in-person rule from 1 Jan 2026 for third-country nationals; found and documented a real URL/content mismatch on the Warszawa 19115 site | Full item-3–5 document list; district routing logic |
| Meldunek | 2 (Warszawa 19115, archived MSWiA) | Resolved the 3-month vs 30-day exemption-threshold discrepancy with verbatim statutory quotes, per citizenship group | Full per-category document list detail |
| EU citizen residence registration | 1 (gov.pl); MOS unreachable (TLS) | 10-year certificate validity vs. indefinite underlying registration, corroborated independently | Sufficient-resources figure; Mazowieckie-specific page unreached |
| Temporary residence and work | 1 (Mazowieckie Office's own page — highest-authority source reached this pass); MOS unreachable (TLS) | Confirmed PLN 4,806 minimum wage figure directly from the competent office; found and dated the 27 April 2026 mandatory-online-portal rule with its Act articles | Fee-tier-to-category mapping; statutory processing-day figure against ISAP directly |
| Temporary residence for studies | 1 (Lubuskie Office — same-Tier stand-in for Mazowieckie); MOS unreachable (TLS) | Resolved the permit-duration structure (15 months / 2 years / duration+3 months capped at 3 years) with an Article 149 citation; identified that the flat PLN 200/500/2500 entry-funds figures do NOT apply here, distinguishing them from the actual PLN 823/1,010 social-assistance-pegged test | Two ministerial regulation citations; ISAP direct read of Art. 149; currency of the 823/1,010 figures |
