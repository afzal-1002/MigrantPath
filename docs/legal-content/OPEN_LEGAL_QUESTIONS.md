# Open Legal Questions — Phase 10

Status: IN PROGRESS. Every item here blocks something specific from becoming `VERIFIED`/
`PUBLISHED` — each entry says exactly what. None of these are guesses filled in to move
faster; per CLAUDE.md ("never fabricate a legal/procedural fact"), an open question stays
open and the affected content stays at `DRAFT`/`READY_FOR_PUBLICATION`/
`BLOCKED_BY_RESEARCH` rather than being published around it.

## Cross-cutting

1. **`mos.cudzoziemcy.gov.pl` is unreachable from this environment** (TLS certificate
   error on every path/subdomain attempted: the EU-registration page, the temporary
   work-permit "wymogi" and "opłata" pages, and the studies-permit "przepisy" page — 4
   distinct URLs, same failure). This is the actual national online portal all four of
   the NATIONAL-jurisdiction MVP procedures route through, so it's a priority follow-up
   for a human reviewer with ordinary browser access, not something to route around by
   substituting other sources. Affects: EU citizen residence registration, Temporary
   residence and work, Temporary residence for studies.

2. **Conditional-personalization gap** — resolved via Option B this phase (see
   `MVP_CONTENT_COVERAGE.md` "Conditional-personalization decision" for the full
   reasoning). Not itself an open legal question, but recorded here because it directly
   limits how precisely the Meldunek citizenship-group-dependent rules (item 4 below) and
   the EU-vs-non-EU document lists can be surfaced to an individual user in their
   personalized checklist this phase.

## PESEL

3. **URL/content mismatch on Warszawa 19115** — the URL slug naming the non-EU/EFTA/UK
   PESEL page in fact serves EU/EFTA/UK-citizen content (both slugs and both renderings
   captured in `PHASE_10_RESEARCH_LOG.md` §1). Blocks creating the `OfficialSource` row
   for the EU/EFTA/UK-citizen variant of this procedure until a human confirms which
   live URL actually serves that content (the site may have already fixed its own
   routing since this pass).

4. **Exact enumeration of "documents confirming data in application items 3–5"** — the
   PESEL application form's own field list needs to be read (the downloadable PDF, not
   yet fetched) to turn this into concrete `DocumentRequirement` rows rather than one
   vague catch-all requirement.

5. Warsaw district/delegation routing logic beyond "any delegation, book an
   appointment" — carried over unresolved from `PROCEDURE_CATALOGUE.md`'s original DRAFT
   note; not investigated further this pass.

## Meldunek

6. Exact enumeration of acceptable "proof of legal residence status" documents per
   applicant sub-category (in particular, the precise UK-citizen document set) needs a
   line-by-line read of the primary Warszawa 19115 text, not just the summary extracted
   this pass.

## EU citizen residence registration

7. **No specific PLN figure found for the "sufficient resources" test** for students and
   economically-inactive applicants — gov.pl's own page states the requirement exists
   without a number. `PROCEDURE_CATALOGUE.md` already flagged this as likely
   case-by-case; this pass did not resolve it either way. Blocks encoding this as a
   `Threshold`; until resolved, any personalization touching this condition must present
   as `NEEDS_CONFIRMATION`/"case-by-case, provide evidence of resources" rather than a
   pass/fail numeric check.

8. The Mazowieckie Voivodeship Office's own procedure page for this specific service
   (as distinct from the national MSWiA/gov.pl summary actually read) was not reached —
   see cross-cutting item 1. May carry Warsaw-specific detail not present nationally.

## Temporary residence and work

9. **Fee-tier-to-applicant-category mapping** (PLN 340 / 440 / 640) — the Mazowieckie
   page lists all three figures; this pass could not confirm from a primary source which
   category (standard employee / board member / other) maps to which figure beyond a
   search-only, non-primary hint that 340 is the board-member tier. Must be confirmed
   against the Act's own fee schedule (Attachment/Annex) before encoding as `Fee` rows —
   getting this wrong would misquote a real fee to a real user.

10. **Statutory processing-time figure** — search-only sources mention a 60-day
    statutory clock, not yet independently confirmed by reading the Act text on ISAP
    directly. Must not be presented to users as a promised number until confirmed, and
    even once confirmed must be presented alongside the caveat (also search-sourced,
    itself needing confirmation) that real-world processing is routinely much longer.

11. `mos.cudzoziemcy.gov.pl`'s wymogi/opłata pages unreached — see cross-cutting item 1.

## Temporary residence for studies

12. **Two ministerial regulations** (dated 25 November 2025 and 10 April 2026, cited by
    the Lubuskie Office page) — exact subject matter and ISAP citation not resolved this
    pass.

13. **Article 149 not read directly against ISAP** — located via search only. Given this
    is the actual statutory basis for the 15-month/2-year/duration+3-months permit
    durations, a direct primary read is the single highest-priority follow-up for this
    procedure before any of those numbers move to `VERIFIED`.

14. **PLN 823 / PLN 1,010 sufficient-funds figures** — these are described as pegged to
    a separate, indexed social-assistance regulation, not fixed in the Act itself. Their
    current-year (2026) value and exact source regulation need confirming before being
    encoded as a `Threshold` — social-assistance figures are adjusted periodically and a
    stale figure here would misstate a real financial requirement to a real applicant.

15. The Mazowieckie-specific version of this procedure page was not reached (Lubuskie's
    was used as a same-Tier, same-national-rule stand-in) — see
    `PHASE_10_RESEARCH_LOG.md` §5 for why this is considered acceptable for the
    *substantive* national rule but not sufficient on its own for `VERIFIED` status.

---

## Resolution tracking

None of the above are resolved as of this document's creation (2026-09-03, Phase 10
research pass). Each blocks a specific downstream artifact from advancing past `DRAFT`/
`READY_FOR_PUBLICATION` — see the per-procedure status table in
`MVP_CONTENT_COVERAGE.md`.
