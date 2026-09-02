# ADR-006: Country classification — groups are explicit membership, free-movement status is derived

Status: Accepted — 2026-09-02 (Phase 3); amended 2026-09-02 (post-approval audit — see
"Why not a universal legal boolean" below)

## Context

The rules engine (Phase 6+) needs to ask "is this country in the EU / EEA / EFTA /
Schengen area as of date X" without ever hard-coding a specific country's treatment
(ARCHITECTURE.md §7, brief §2: never `Pakistan → Work TRC` / `Germany → EU
registration`). Country group membership also genuinely changes over time (the UK's EU
membership ending 2020-01-31 is the textbook case), so it cannot be a static boolean.

A related question the brief poses directly (§6): should `THIRD_COUNTRY` — "not an
EU/EEA/EFTA/Swiss national" — be its own stored `CountryGroupMembership`, seeded for
every one of the ~220 countries that qualify, or derived at query time from the
*absence* of EU/EEA/EFTA/Swiss membership?

## Decision

**Explicit, time-bounded membership** (`CountryGroupMembership.valid_from`/`valid_to`)
for every real, positive classification: `EU_MEMBER`, `EEA`, `EFTA`, `SCHENGEN`, and one
deliberately-labeled convenience aggregate, `EU_EEA_SWISS` (`group_type = 'CONVENIENCE'`,
everything else `'LEGAL'` — see V9's `country_groups.group_type` CHECK constraint).

**No `THIRD_COUNTRY` group is ever stored** — it does not exist as a `CountryGroup` row at
all. `CountryClassificationService.isOutsideEuEeaSwissFreeMovementGroup(countryCode,
date)` (originally named `isThirdCountry` — renamed, see "Why not a universal legal
boolean" below) derives it as "not a current member of `EU_MEMBER`, `EEA`, or `EFTA`" at
the given evaluation date (Switzerland is covered via its `EFTA` membership, no separate
check needed).

**`UK_WITHDRAWAL_AGREEMENT` is not modeled as a `CountryGroup` at all** (brief §44) — WA
rights depend on whether a *specific person* was already exercising free-movement
rights before the end of the transition period, not on holding UK nationality in
general. That is a person-level status fact, not a country classification, and belongs
to a future phase's user/case model, not reference geography.

## Why derive it rather than store a THIRD_COUNTRY group

- **~220 rows of pure negative information add no query-time value.** "Is Pakistan
  outside the free-movement group" and "is Pakistan *not* in EU/EEA/EFTA" are the same
  computation either way; storing the negative doesn't make the positive check faster or
  simpler.
- **A missing membership row would be ambiguous.** If a `THIRD_COUNTRY`-style group were
  stored, seeing no row for a newly-added country (a new ISO code, a country not yet
  re-seeded after a boundary change) could mean "definitely outside the group" or "not
  classified yet" — indistinguishable without checking every other group too. Deriving it
  removes that ambiguity entirely: the result is true if and only if the country fails
  the positive checks, full stop, no separate seed step to forget.
- **Every future addition to the EU/EEA/EFTA (or a country leaving) automatically and
  correctly flips a country's derived result** with a single membership row change,
  rather than needing a second, easy-to-forget update to a stored group.

## Why not a universal legal boolean (post-approval audit, 2026-09-02)

The original name, `isThirdCountry`, was itself a mistake worth recording. "Third-country
national" is a real legal term, and **different EU/Polish legal instruments define it
differently** — most concretely, around people who hold equivalent free-movement rights
without being EU/EEA/EFTA nationals in the strict membership sense:

- A **Swiss national** is not an EU, EEA, or EFTA-as-relates-to-EEA national in the
  narrow sense checked here, yet holds free-movement rights in Poland via the 1999
  EU-Swiss bilateral agreement (in force 2002-06-01) — captured by the `EU_EEA_SWISS`
  convenience group, not by `EFTA` membership implying it.
- A **UK national covered by the Withdrawal Agreement** retains specific residence
  rights despite the UK holding no `EU_MEMBER`/`EEA`/`EFTA` membership since 2020-01-31 —
  a fact about that *specific person's* case history, never a property of the `GB`
  country row (see the existing "UK_WITHDRAWAL_AGREEMENT is not a CountryGroup" decision
  above, which was already correct — the naming risk was elsewhere).

A single global `isThirdCountry(code, date) → boolean` invites exactly the failure mode
ARCHITECTURE.md §7/brief §2 already warn about for nationality generally: a future Phase
6 rule author sees a boolean named after the legal term they need and wires it in
directly, silently adopting *this* method's specific definition (not-EU-not-EEA-not-EFTA)
as if it were *the* legal definition for their procedure, when their procedure's actual
governing instrument might define it differently (e.g. explicitly carving out Swiss
nationals or Withdrawal Agreement beneficiaries).

**Fix**: renamed to `isOutsideEuEeaSwissFreeMovementGroup(code, date)` — a name that
describes exactly and only what it computes (absence from three specific country
groups), with no legal claim attached. `ASSESSMENT_DECISION_TREE.md`'s eventual
`THIRD_COUNTRY_NATIONAL` classification (Step 1) remains a *separate*, person-level,
procedure-aware concept a future phase computes from this structural fact **plus** other
inputs (a Withdrawal Agreement case flag, a stateless flag, the specific procedure's own
legal definition) — never a direct alias for this method. Phase 6's rules engine, not
Phase 3's reference layer, is where "which legal definition of third-country-national
applies to procedure X" gets decided, on a per-procedure basis if needed.

`SCHENGEN` remains completely independent of this (and any future) free-movement/
residence-rights classification — it is border-control cooperation, not a residence-
rights framework, and no classification here or later should derive residence rights
from Schengen membership alone (proven by
`CountryClassificationServiceTest#isOutsideEuEeaSwissFreeMovementGroup_schengenAloneDoesNotExemptACountry`).

`EFTA` and `EEA` also remain genuinely distinct groups, not aliases — Switzerland holds
only `EFTA`; Iceland, Liechtenstein, and Norway hold both (proven by
`CountryGroupMembershipRepositoryTest#switzerland_isEftaButNotEea_realSeedData` and
`#iceland_liechtenstein_norway_areBothEftaAndEea_realSeedData`, against real seed data,
not mocked fixtures).

## Membership provenance (post-approval audit, 2026-09-02)

`CountryGroupMembership.provenanceStatus` (V19, `MembershipProvenanceStatus`:
`VERIFIED`/`DRAFT`) makes V11's own "pre-2000 accession dates are approximate" caveat a
real, queryable column instead of only a migration comment. Every row with `valid_from <
2000-01-01` is seeded `DRAFT`; everything else is `VERIFIED`. This doesn't re-verify any
date (see REFERENCE_DATA_SOURCES.md — that remains open, deliberately not rushed) — it
only makes the existing confidence level visible, so a future legally-significant rule
evaluation *can* require `VERIFIED` provenance where appropriate, rather than silently
inheriting an unverified historical date with no way to tell the difference.

## Temporal convention (brief §16)

Reference-data validity is checked as `active = true AND valid_from <= evaluationDate
AND (valid_to IS NULL OR valid_to >= evaluationDate)` — **`valid_to` inclusive**. This
is deliberately different from the Procedure/Rule/Threshold "Active-Version Predicate"
(DATABASE.md §0), which uses an *exclusive* `effective_to`. Both conventions are
internally consistent; they just answer different questions — "what was this country's
**last day** as an EU member" (inclusive, a calendar-day fact) versus "at what **instant**
does a new legal-content version take over from the old one" (exclusive, a cutover
instant). Using the same operator for both would make one of the two read unnaturally.

## Consequences

- A rule that needs the free-movement-group structural fact calls
  `isOutsideEuEeaSwissFreeMovementGroup` — there is no table to consult and possibly get
  out of sync — but a rule that needs an actual legal "third-country national"
  determination for a specific procedure must combine that fact with whatever else that
  procedure's governing instrument requires, in Phase 6, not treat this method's result
  as the whole answer.
- Adding a new `CountryGroup` later (e.g. a genuine future need) is additive; no
  existing derived logic needs to change.
- `UK_WITHDRAWAL_AGREEMENT`-style person-level eligibility remains explicitly out of
  scope for reference data — a future phase must model it against a case/person, not
  retrofit it as a country classification.
- `MembershipProvenanceStatus` gives Phase 6 a way to require `VERIFIED` provenance for a
  legally-significant rule condition, without reference data needing the full
  `OfficialSource` review workflow legal content uses.

See [DATABASE.md §2](../../database/DATABASE.md) and
[REFERENCE_DATA_SOURCES.md](../../reference/REFERENCE_DATA_SOURCES.md) for the actual
membership data and its provenance.
