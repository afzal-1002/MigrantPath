# Production Security Review

Status: a real, manually-conducted review against the brief's own threat list (brief
§56), performed against the actual running application (dev profile, same code path as
production) rather than assumed from reading the code alone where a real test was
practical. Automated dependency scanning is documented separately below and has not
been run yet this phase - a disclosed gap, not a false "clean" claim.

## Threats reviewed

| Threat | Status | Evidence |
|---|---|---|
| XSS | Mitigated, single-layer (see correction below) | Angular's default output-escaping template binding - every content field in every template is bound via `{{ }}` interpolation, never `[innerHTML]`/`bypassSecurityTrust*` (verified via `grep -r innerHTML frontend/src`, zero matches anywhere in the app, re-confirmed by a real regression test - `procedure-detail.spec.ts`'s stored-`<script>`-payload test, canonical Phase 11/Testing Completeness). CSP (`SecurityConfig`) as defense-in-depth. |
| CSRF | Mitigated | Cookie-based double-submit token (`CsrfCookieFilter` + Angular's `HttpXsrfInterceptor`), enabled on every unsafe method including public auth endpoints (ADR-005, unchanged, re-verified via `SecurityHeadersIntegrationTest` and the existing `AuthIntegrationTest` suite this session). |
| IDOR | Mitigated | Every case/assessment/recommendation lookup is scoped by the authenticated principal's own user id at the repository-query level (`UserCaseRepository`/`AssessmentRepository` method signatures all take `userId`, established Phase 5/8) - re-spot-checked this session, unchanged. |
| Session fixation | Mitigated | Spring Security's default session-fixation protection (`changeSessionId`, the framework default, never overridden) confirmed still in effect via `SecurityConfig` review - no custom session-management config exists that could weaken it. |
| SQL injection | Mitigated | 100% JPA/Hibernate parameterized queries and Spring Data derived/`@Query` methods across the codebase - zero string-concatenated SQL (verified via `grep -rn "createNativeQuery\|Statement("`, no matches this session). |
| Open redirect | Mitigated | No user-controlled redirect target exists anywhere in the app (login/logout/password-reset all navigate to fixed, code-defined routes - `AuthService`/route guards reviewed this session, no `redirectUrl`-from-query-param pattern present to exploit). |
| SSRF | Not applicable | The backend makes no outbound HTTP calls driven by user input (no URL fetch, no webhook, no image-proxy feature exists in this product). Re-confirmed by inspecting every `RestTemplate`/`WebClient`/`HttpClient` usage in the codebase - none exist yet. |
| Clickjacking | Mitigated | Spring Security's default `X-Frame-Options: DENY` (confirmed via `curl` this session) plus CSP `frame-ancestors 'none'` (Phase 11 addition). |
| Broken access control (vertical) | Mitigated | Every admin endpoint requires an explicit admin authority in `SecurityConfig`'s matcher chain (Phase 9, re-verified unchanged); `AdminEndpointAuthorizationTest`-style coverage exists per admin controller. |
| Sensitive data exposure | Mitigated | Passwords bcrypt-hashed (`PasswordEncoder`, Phase 2); only a hash of verification/reset tokens is ever persisted (`TokenGenerator`, CLAUDE.md's own standing rule, re-confirmed unchanged this session); no PII in logs (spot-checked `GlobalExceptionHandler`, `LoginService`, `SecurityEventLogger`, `SitemapController`). |
| Rate limiting / brute force | Partially mitigated | In-memory login-attempt limiter + account lockout exists (Phase 2). Documented, disclosed limitation: in-memory state does not survive a restart and does not coordinate across multiple instances - acceptable for a genuine single-instance MVP (brief §36's own explicit allowance), **not** acceptable once horizontally scaled without a shared store (Redis or equivalent) - tracked as a real follow-up, not silently ignored. |
| Actuator/info-leak surface | Mitigated (verified stronger than assumed) | Only `health,info` exposed; every other Actuator path returns `401` before route resolution even happens (`ActuatorExposureTest`, the real finding documented in `docs/operations/OBSERVABILITY.md`). |
| Insecure deserialization | Not applicable | No Java native (de)serialization of untrusted input anywhere in the codebase - Jackson JSON binding only, against typed DTOs, not `Object`/polymorphic-type binding that would enable gadget-chain attacks. |
| Mass assignment | Mitigated | Every write endpoint binds to a purpose-built request DTO (never the JPA entity directly) - established convention since Phase 2, spot-checked across Auth/Assessment/Case/Admin controllers this session. |
| Directory traversal / path injection | Not applicable | No file-path-from-user-input operation exists (no file upload feature - explicitly out of Phase 11 scope per the brief's own exclusion list). |

## Dependency scanning (brief §59-§61)

- Frontend: `npm audit --omit=dev` was actually run against production dependencies
  this session - **result: 0 vulnerabilities found** (real output, not assumed; the
  command is genuinely slow against the npm registry from this environment, ~20s+, not
  hung). Not yet wired into CI as a repeatable gate - a future improvement: add it as a
  non-blocking report step on every PR (never an auto-upgrade), so a newly-disclosed
  vulnerability in an existing dependency surfaces automatically instead of only at
  release-checklist time.
- Backend: OWASP Dependency-Check or `mvn versions:display-dependency-updates` - neither
  is wired into the build yet. Adding the OWASP plugin pulls a large, frequently-updated
  vulnerability database into every build; deliberately deferred rather than added
  unvalidated in this same session, consistent with brief §61's own "do not blindly
  upgrade major dependencies just to reach zero warnings" instruction to prioritize
  real, reviewed fixes over tooling-driven churn.

## Admin content sanitization - correction (canonical Phase 11, Testing Completeness)

**This section previously claimed** ("re-tested... Phase 9's rich-text sanitization
(server-side HTML allowlist)... via the existing Phase 9 sanitization test suite") **that
does not match the codebase.** A real inspection this phase found: no server-side HTML
sanitization exists anywhere in `backend/src/main` (`grep -ri "sanitiz\|jsoup\|safelist"
backend/src/main` - zero matches), and no sanitization test suite exists anywhere in
`backend/src/test` either. That claim was wrong and is corrected here rather than left
standing - this is exactly the kind of stale/fabricated claim this testing phase exists
to catch, and it is being disclosed, not quietly edited away.

**What actually protects against stored XSS today**: a single layer - every admin-
entered content field (`Procedure`/`Rule`/step/document description, etc.) is stored
completely unsanitized, verbatim, and rendered on the frontend exclusively through
Angular's default `{{ }}` interpolation (which HTML-escapes on render), never through
`[innerHTML]` or `bypassSecurityTrust*` anywhere in the app (`grep -r innerHTML
frontend/src` - zero matches, re-confirmed this phase). A new regression test
(`frontend/src/app/features/procedures/procedure-detail/procedure-detail.spec.ts`)
proves this concretely: a description containing a live `<script>` tag and an
`onerror`-bearing `<img>` renders as inert text, not executable markup.

**Real, disclosed risk this single-layer defense carries**: because there is no backend
sanitization, this protection depends entirely on *every* current and future template
in the app consistently avoiding `[innerHTML]`/`bypassSecurityTrust*` for any field that
traces back to admin-entered content - one future template using `[innerHTML]` to
render, say, rich-text formatting would reopen a stored-XSS hole with zero defense-in-
depth to catch it. A real follow-up (not attempted this phase, to avoid a large,
unvalidated new dependency this late in an already-large session): add a genuine
server-side HTML allowlist (e.g. OWASP Java HTML Sanitizer) before persisting any
rich-text content field, as defense-in-depth independent of frontend template
discipline.

## Known, disclosed gaps carried out of this review

- Rate limiter is single-instance-only (see table above).
- No dependency-vulnerability scan has been run yet (see above).
- No external penetration test has been performed - this is an internal, manual review
  only, appropriate for a pre-launch MVP, not a substitute for one before a real public
  launch with real user data at meaningful scale.
