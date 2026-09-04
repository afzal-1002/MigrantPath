# Production Email

Status: the application's mail abstraction (`spring-boot-starter-mail`, standard
`JavaMailSender`) is provider-agnostic already - no code change is needed to point it at
a real transactional-email provider, only configuration. **No real SMTP provider has
actually been wired up or tested against production config in this session** - this
document is the procedure for doing so, not a claim it's already done.

## Configuration

`application-production.yml`/`application-staging.yml` already require (fail-fast if
missing):

```yaml
spring:
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
```

Point these at any standard SMTP provider (Amazon SES, Postmark, SendGrid, Mailgun,
etc. - no provider is hard-coded or preferred by the application itself). Enable TLS via
the provider's documented port/settings (`spring.mail.properties.mail.smtp.starttls.
enable=true` if the provider needs it explicitly - add to the profile file once a real
provider is chosen, since the exact property needed is provider-specific).

## Security (brief §55)

- **TLS required** - never send credentials/content over plaintext SMTP to a real
  provider.
- **Never log SMTP credentials** - already true (`MAIL_USERNAME`/`MAIL_PASSWORD` are
  plain Spring config properties, never logged by any code in this codebase; confirmed
  by the existing "no sensitive logging" discipline `GlobalExceptionHandler`/
  `SecurityEventLogger` already follow).
- **Never log verification/reset tokens** - already true; `TokenGenerator`'s own
  Javadoc states only a hash of the token is ever persisted, and no code path logs the
  raw token (CLAUDE.md's own standing rule, unchanged by this phase).

## Deliverability (brief §56 - not yet configured, honestly disclosed)

Before real email volume, the chosen sending domain needs:

- **SPF** record authorizing the provider to send on the domain's behalf.
- **DKIM** signing configured with the provider.
- **DMARC** policy for the domain.
- A **verified sender domain/address** with the provider (not a shared/generic default
  sender).

None of these are configured yet - there is no real production domain/provider chosen
at the time of this document. Do not claim SPF/DKIM/DMARC are "done" until a real
provider integration actually sets them up and this document is updated to name the
real domain and confirm it.

## Failure policy (brief §57 - already implemented, Phase 2)

- **Verification email fails to send**: the account remains `PENDING_VERIFICATION`; the
  user can request a resend (`POST /api/v1/auth/resend-verification`, rate-limited).
  Registration itself still succeeds (`RegistrationService` does not roll back account
  creation on an email-send failure) - the account simply can't log in until verified.
- **Password-reset email fails to send**: the API still returns the same generic
  response regardless of whether the account exists or the email succeeded
  (`PasswordResetService`'s deliberate non-enumeration design) - a send failure is an
  operational problem to catch via logs/metrics, never something the API response
  reveals to the caller.
- Both failure paths already log the operational error server-side (see
  `docs/operations/OBSERVABILITY.md`'s `email.send.failure` metric, once wired).

## Mailpit stays test/local-only (brief §54/§167)

Never point `MAIL_HOST` at `localhost`/Mailpit in staging or production -
`application-{staging,production}.yml` both require `MAIL_HOST` with no fallback, so a
misconfiguration here fails startup rather than silently working against a fake local
mail catcher. There is no automated *runtime* check beyond that fail-fast requirement
(brief §167's "at minimum document/validate production mail host" - the "require it,
no default" mechanism already in place is the validation; a stronger check, e.g.
rejecting `localhost`/private-IP values outright, is a reasonable future hardening not
implemented this phase).
