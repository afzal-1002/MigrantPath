# Phase 13.5 (Bridge) Report — Production-Bundle Assessment Regression Fix

Status: ✅ **RESOLVED.** The deterministic, production-build-only regression in the
Assessment flow's "current legal status" dropdown is fixed, root-caused precisely (not
guessed), and the full production-image Playwright suite is green.

## Executive Summary

The regression is resolved. Root cause: Angular's default production build defers the
global stylesheet's activation via the classic `media="print" + onload="this.media=
'all'"` loading trick, which depends on an **inline event-handler attribute** executing
in the browser. This application's own Content-Security-Policy (`script-src 'self'`, no
`unsafe-inline`) — correct, and unchanged by this fix — silently blocks that inline
handler. The consequence: the **entire global stylesheet** (`src/styles.scss`, compiled
to `styles-*.css`) never actually applied on screen in production, including the
`pointer-events: none !important` rule on `.mat-mdc-floating-label`/`mat-label` that
Phase 5 had already shipped once to fix this exact class of Material floating-label
click-interception bug. The fix disables only the `inlineCritical` CSS-optimization
sub-option in `angular.json`'s production configuration — one line, no CSP change, no
AOT/minification/tree-shaking change, no application code change.

## Reproduction

- **Route**: `/assessment/:id`, "Current status" section (Step 2 of 3 in the seeded
  MVP questionnaire).
- **Question code**: `CURRENT_LEGAL_STATUS` (section `CURRENT_STATUS`, `SINGLE_SELECT`,
  `STATIC` option source, seeded in `V38__seed_warsaw_general_assessment.sql`) — visible
  once `CURRENTLY_IN_POLAND` (an earlier, different-section question) is answered
  `true`, a real cross-section dependency reveal.
- **Environment**: the exact real release images (`docker build` from this repo's
  `backend/Dockerfile`/`frontend/Dockerfile`, no build args beyond `BUILD_COMMIT`),
  run via `infra/docker-compose.prod.yml`'s topology (nginx reverse proxy → Spring
  Boot → PostgreSQL), `Playwright BASE_URL=http://localhost:18080`, Chromium (the
  version bundled with this repo's pinned Playwright version), against the real
  content-populated dev PostgreSQL (36 published `ProcedureVersion`s, 6 published
  `RuleVersion`s — a from-empty self-hosted Postgres has zero published content by
  design, "schema ready ≠ content provisioned," and was ruled out as a separate,
  correct, non-bug behavior during this investigation, not the regression itself).
- **Exact failure**: `page.getByRole('combobox', {name: 'What is your current legal
  status in Poland?'}).click()` times out after 30s / 50+ actionability retries.
  Playwright's own diagnostic identifies the intercepting element precisely: `<mat-label
  _ngcontent-ng-c971017976="">...</mat-label> from <div matformfieldnotchedoutline=""
  class="mdc-notched-outline...">…</div> subtree intercepts pointer events`. This is
  category (c) from the brief's own list: **options render but cannot be selected** —
  specifically, the trigger is visible, enabled, and stable, but a sibling label element
  sits on top of it and absorbs the click.
- **Why production-only**: `ng serve` (development configuration) has `"optimization":
  false` in `angular.json`, so Angular never emits the deferred-stylesheet pattern at
  all in dev — confirmed directly (`curl localhost:4200/` shows a single plain `<link
  rel="stylesheet" href="styles.css">`, no `media="print"`, no `onload`). The bug is not
  a dev/prod *logic* difference — the compiled TypeScript/HTML/CSS content is identical
  either way — it is a dev/prod *CSS-delivery-mechanism* difference that only the
  production build path uses, combined with a security header (CSP) that only the real
  reverse proxy sends (`ng serve` sends no CSP header at all — `ENVIRONMENTS.md`).

## Root Cause

`angular.json`'s production build configuration did not override `optimization.styles.
inlineCritical`, so it took the Angular CLI's default of `true`. With `inlineCritical:
true`, the `@angular/build:application` builder splits the global stylesheet into (a)
a small "critical" CSS block inlined directly in `index.html`'s `<head>` and (b) the
*remaining* CSS, loaded asynchronously via the standard "loadCSS" pattern to avoid
render-blocking:

```html
<link rel="stylesheet" href="styles-F5OFQAOV.css" media="print" onload="this.media='all'">
<noscript><link rel="stylesheet" href="styles-F5OFQAOV.css"></noscript>
```

A browser only ever applies a `media="print"` stylesheet when printing; the `onload`
handler is what flips `media` to `"all"` once the file has loaded, activating it for
screen rendering. The `<noscript>` fallback is inert in any real browser (scripting
is always enabled). This means the **only** path that ever activates the stylesheet for
screen rendering is that one inline `onload` attribute executing.

This application's Content Security Policy — `script-src 'self'` (`frontend/security-
headers.conf`), a deliberate hardening choice, not an oversight — has no `'unsafe-
inline'` or hash/nonce allowance for inline **event-handler attributes** (the
`script-src-attr` sub-directive, which inherits from `script-src` when not set
separately). Chrome therefore blocks the `onload` attribute from ever executing,
logging exactly this on every affected page load (captured directly via a
`securitypolicyviolation` event listener during this investigation, not inferred from
the generic console text alone):

```json
{ "violatedDirective": "script-src-attr", "blockedURI": "inline", "disposition": "enforce" }
```

Because `media` never flips to `"all"`, the global stylesheet — despite being fully
fetched, parsed, and present in `document.styleSheets` with its rules syntactically
intact — **contributes nothing to the render tree**. This includes the exact rule Phase
5 already shipped once for this precise bug class:

```scss
mat-label, .mat-mdc-floating-label {
  pointer-events: none !important;
}
```

Without it, the MDC floating label (a real, separate `<label class="mdc-floating-
label...">` wrapping the projected `<mat-label>` content, confirmed via direct DOM
inspection) defaults to `pointer-events: all`, and — because it visually overlaps the
`mat-select` trigger whenever the label hasn't animated to its "floated" position
(itself a known consequence, already documented in `styles.scss`, of this codebase
deliberately not installing `@angular/animations`) — it absorbs the click a real user's
mouse would otherwise deliver to the select.

## Evidence

All gathered against the real production-like stack, not inferred:

1. **`getComputedStyle(label).pointerEvents === "all"`**, confirmed independently three
   ways (`getComputedStyle` one-arg, two-arg, and Chrome DevTools Protocol's
   `CSS.getComputedStyleForNode`, which reads the browser engine's own resolved value
   directly, bypassing JS entirely).
2. **The CSS rule genuinely exists, is syntactically valid, and textually matches the
   exact failing element** — confirmed via `document.styleSheets` enumeration,
   `Element.matches()` (both the bare and combined selector), and `querySelectorAll`.
   Ruled out: `@layer`/`@scope` wrapping (grepped the real production CSS, zero
   matches), Shadow DOM encapsulation (`getRootNode()` confirms light DOM),
   `document.adoptedStyleSheets` (empty), a competing `pointer-events` declaration
   anywhere else in any of the 18 stylesheets on the page (only one `pointer-events`
   declaration exists in the entire production CSS output, and it's this one), and an
   inline style override (`getAttribute('style')` was `null`/empty).
3. **Forcing the same rule via `element.style.setProperty('pointer-events', 'none',
   'important')` immediately fixes hit-testing** — proving the mechanism (CSS
   `pointer-events` controlling `elementFromPoint`/click-target resolution) is exactly
   right; only the *delivery* of the rule was broken.
4. **The decisive finding**: enumerating every stylesheet's `disabled`/`media`
   properties (not just its rules) found the global stylesheet's `<link>` reporting
   `media: "print"` — invisible to `Element.matches()`/`CSSStyleRule.selectorText`
   (purely syntactic checks) but authoritative for whether the browser's cascade
   actually uses it.
5. **Directly confirmed in the served HTML** (`curl http://localhost:18080/`):
   ```html
   <link rel="stylesheet" href="styles-F5OFQAOV.css" media="print" onload="this.media='all'">
   <noscript><link rel="stylesheet" href="styles-F5OFQAOV.css"></noscript>
   ```
6. **Dev vs. production comparison**: `ng serve` (`localhost:4200`) serves a single
   plain `<link rel="stylesheet" href="styles.css">` with no `media`/`onload` — because
   the development build configuration disables `optimization` entirely, so
   `inlineCritical` never activates.
7. **Backend API response was compared and eliminated as a cause early** — the
   `CURRENT_LEGAL_STATUS` question definition (code, `SINGLE_SELECT` type, 12 stable
   option codes, `required: true`, no `allowUnsure` widening) is identical whether
   fetched through the dev backend or the production-image backend; both serve the
   same `AssessmentDetail` JSON shape from the same versioned `QuestionnaireVersion`
   row. Confirmed via direct inspection before any frontend investigation began (brief
   §4's own required ordering).

## Fix

`frontend/angular.json`, production build configuration:

```json
"optimization": {
  "scripts": true,
  "fonts": true,
  "styles": {
    "minify": true,
    "inlineCritical": false
  }
}
```

This is the only code change. No Angular component, template, or global CSS file was
touched — the `pointer-events: none !important` rule from Phase 5 was always correct;
it simply never had a chance to run.

## Why This Fix Is Correct

It fixes the actual state/delivery problem, not a symptom or a timing race:

- **Not a workaround**: no `setTimeout`, no forced click, no `page.evaluate`-driven
  value assignment, no test-selector change, no `ApplicationRef.tick()` hammer. The
  application itself now behaves identically for a real mouse or keyboard user in
  production as it already did in dev.
- **Root-cause-targeted**: `inlineCritical` is a narrow CSS *delivery strategy* flag.
  Disabling it does not touch AOT compilation, JS minification/tree-shaking, source
  maps, or output hashing — confirmed by an identical initial bundle size before and
  after (`400.98 kB` raw / `106.73 kB` estimated transfer, both builds) and an
  unchanged JS chunk list.
- **CSP was correctly left alone.** The brief explicitly warns against loosening CSP to
  paper over a UI bug; the real defect was that Angular's own build output assumed a
  CSP permissive enough to run an inline event handler, which this application's CSP
  (deliberately, correctly) never was. The fix makes the *build output* compatible with
  the existing, unweakened security policy — not the other way around.
- **Deterministic, not probabilistic**: the bug was already 100% reproducible before
  the fix and never reproduced afterward across every real run performed this phase
  (single-spec runs, full-suite 1-worker runs, full-suite 3-worker runs, with and
  without the pre-existing unrelated country-autocomplete flake in play).

## Security Impact

**None.** Explicitly confirmed:
- CSP: unchanged (`frontend/security-headers.conf` was not touched).
- Security headers: unchanged.
- Cookie/CSRF policy: unchanged.
- Source-map production policy: unchanged (still no source maps shipped in the
  production build — `angular.json`'s production configuration does not set
  `sourceMap: true`, and this phase did not add it).

## Regression Tests

1. **`scripts/release-smoke.sh`** (extended) — a new, non-destructive check asserting
   the served `/` response never contains `media="print"` on its stylesheet `<link>`.
   This is the most direct possible test of the *actual root cause* (not a downstream
   symptom): it would have failed on every single run against the pre-fix image and
   passes on every run against the fixed one — confirmed both ways this phase. Runs
   automatically as part of every release smoke check going forward
   (`docs/releases/PRODUCTION_RELEASE_CHECKLIST.md`).
2. **`frontend/e2e/assessment.spec.ts`** (pre-existing, unmodified) — all three
   scenarios (the full work-branch guided flow through to case creation, the
   branch-removal same-section reveal, and the logout/resume round trip) are the real,
   already-existing "targeted Playwright regression" the brief asks for: each was
   **deterministically failing before this fix and deterministically passing after**,
   confirmed by direct A/B runs against the identical database/content in this
   session. No new Playwright spec was added on top of these, since they already
   constitute exactly the flow the brief's own §36 template describes (open Assessment
   → current legal status → answer persisted → branch continues → recommendation).
3. **No new frontend unit (Vitest/TestBed) test was added.** The root cause is a build
   *configuration* value, not application TypeScript/component logic — Angular's own
   unit-test builder does not exercise `angular.json`'s production build path (unit
   tests run against a `development`-equivalent, unoptimized bundle, so they would
   never have caught this regression and can't usefully guard it either). The
   `release-smoke.sh` check above, which validates the *actual compiled and served*
   artifact, is the correct layer for this class of bug and was judged more trustworthy
   than a config-file field assertion (which could pass while a different code path
   reintroduces deferred loading through some other mechanism).
4. **Backend**: no backend code changed this phase; the full existing backend suite
   (362 tests) was re-run for completeness and remains green (see "Final Test
   Results").

## Guided-Flow Verification

Real, through the deployed stack, this phase (`assessment.spec.ts` Scenario 1):

```text
Assessment (About You: Pakistan citizenship, currently in Poland = Yes, DOB)
  ↓
Current legal status selected ("I am not sure", via a real mouse click - no workaround)
  ↓
Answer persisted (PUT /api/v1/assessments/{id}/answers/CURRENT_LEGAL_STATUS - 200)
  ↓
Branch continues (Goals section reveals Work; HAS_JOB_OFFER reveals salary/contract
  in the same section without leaving it - the pre-existing same-section reveal
  mechanism, unaffected by this fix and re-confirmed working)
  ↓
Rule evaluation (real production TEMP_RESIDENCE_WORK_BASE/MIN_WAGE rules against the
  real content-populated database)
  ↓
Recommendation ("Your pathways" / "Most relevant" - a real PRIMARY_MATCH badge on
  "Temporary residence and work")
  ↓
Case creation succeeds
```

Scenario 2 (removing Work mid-branch, same-section hide behavior) and Scenario 3
(logout mid-assessment, resume with the answer preserved after a real re-login) both
independently re-confirm the fix holds under different navigation paths, not just the
happy path.

## Production Image Verification

- **Frontend image**: `foreigner-warsaw-frontend:<commit-sha>`, rebuilt clean (`docker
  build --load`, then a `--no-cache` rebuild earlier in this investigation to rule out
  a stale layer, then a normal cached rebuild after the `angular.json` fix — all three
  builds produced consistent, correct output).
- **Backend image**: `foreigner-warsaw-backend:<commit-sha>` — unchanged this phase,
  reused as-is (no backend code was touched).
- **Reverse proxy**: `infra/docker-compose.prod.yml`'s real nginx container, unmodified.
- **Production build mode**: the real `production` Angular configuration (`npm run
  build`, no diagnostic flags, `optimization` fully enabled except the one narrow
  `inlineCritical: false` change).
- **Playwright base URL**: `http://localhost:18080` (the local production-like stack's
  reverse-proxy port), targeting the real content-populated dev PostgreSQL for the
  guided-flow/recommendation checks and (for the initial isolated reproduction only) a
  disposable fresh self-hosted PostgreSQL to independently confirm the "schema ready ≠
  content provisioned" behavior was correct, unrelated design, not a bug.

## Final Test Results

- **Backend**: `./mvnw verify` — 362/362 tests, 0 failures, 0 errors, BUILD SUCCESS
  (unchanged from Phase 13's own final number; no backend code changed this phase, run
  again for completeness per the brief's own §59).
- **Frontend unit**: 121/121 passed (27 files), lint clean, production build clean
  (identical bundle size to before the fix).
- **Playwright, full suite, canonical 3-worker policy, against the real release
  images**: **18/18 passed** (17 clean on the first attempt; 1 — Scenario 1 of
  `assessment.spec.ts` — flaked on its *first* attempt for the exact pre-existing,
  already-documented (Phase 11) country-reference-data-autocomplete worker-contention
  reason, unrelated to this fix, and passed cleanly on retry #1, matching this
  project's own established, documented mitigation). **Zero tests skipped as a
  consequence of the fixed regression** — Scenarios 2 and 3, previously skipped every
  single time because Scenario 1 never got past the dropdown, now run and pass every
  time.

## Related Pattern Audit

The root cause was a single, global build-configuration value affecting the *entire*
compiled stylesheet, not a per-component code pattern repeated across files (unlike,
say, a bad `@for track` expression that could exist independently in multiple
templates) — so there is no second instance of "the same bug" to find and fix
elsewhere in the codebase; the one `angular.json` change fixes every page and every
component that depends on any rule in `src/styles.scss`, uniformly. This was verified
empirically rather than assumed: the full Playwright suite exercises Material
dropdowns/selects on multiple other pages — the country/region/city/district
reference-data cascade (`reference-data.spec.ts`), the Work-goal checkbox group and
salary/contract fields (`assessment.spec.ts` Scenarios 1-2), and the Admin panel's own
procedure/rule/source editors (`admin.spec.ts`, all 7 sub-scenarios, each driving real
Material form controls through the real UI) — all pass cleanly against the fixed
image, confirming no other Material control anywhere in the app was silently relying on
a different, still-broken delivery path.

## Bugs Found

No additional genuine bugs were found during this bridge phase beyond the one this
phase exists to fix. (The "schema ready ≠ content provisioned" behavior encountered
while first reproducing against a fresh disposable database is expected, by-design,
already-documented Phase 13 behavior, not a new finding.)

## Deviations

- Section 35's "targeted frontend unit test" was deliberately not added as a
  Vitest/TestBed spec — see "Regression Tests" item 3 above for the reasoning (Angular's
  unit-test builder doesn't exercise the production build path where this bug lived,
  so a unit test there would be decorative, not protective). The equivalent, more
  trustworthy coverage was added at the build/smoke-check layer instead
  (`scripts/release-smoke.sh`).
- A brief, disposable fresh-database detour (Section 1's "confirm ... user/test
  fixture") surfaced the correct, pre-existing "no content on a fresh database"
  behavior; this cost some investigation time but is explicitly why brief §4 requires
  confirming the backend/data layer before assuming a frontend cause, and it directly
  informed the final environment choice (real content-populated database) used for the
  actual verification.

## Known Issues

- The 32 stale `TEST_*` procedures already disclosed in `PHASE_13_REPORT.md` remain in
  the shared local dev database — a documented dev-environment hygiene issue, not
  touched this phase (no disposable reset was needed for this fix; the dev database
  used for final verification was read from, never mutated destructively, beyond the
  ordinary account/assessment rows any real Playwright run creates).
- The country-autocomplete worker-contention flake (Phase 11, re-observed once this
  phase on Scenario 1's first attempt) remains exactly as documented — out of this
  bridge phase's scope, unrelated root cause, already mitigated by the existing
  3-worker/retry policy.
- The `UnknownHostException`-style indirect error message for a missing required
  backend env var (`PHASE_13_REPORT.md`'s own disclosed minor gap) remains unfixed —
  out of scope for this bridge phase.

## Phase 13 Release Blocker

**RESOLVED.**

## Canonical Phase 14 Readiness

**READY.** The production-image critical assessment flow (guided assessment → real
Material dropdown interaction → answer persistence → branch continuation → rule
evaluation → recommendation → case creation) is green, and the complete required
Playwright suite (18/18) passes against the real release images through the real
reverse proxy. Canonical Phase 14 (Monitoring/Analytics) was not started, per
instruction — this bridge phase stops here.
