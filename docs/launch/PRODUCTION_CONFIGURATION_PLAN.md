# Production Configuration Plan

Post-MVP Milestone L1. Every production variable this project actually references
(source of truth: `docs/operations/ENVIRONMENT_VARIABLES.md`, verified by grep against
`application-production.yml`/`infra/docker-compose.prod.yml` — not written from
memory), with ownership and rotation added. **No values appear anywhere in this
document.**

## Database

| Variable | Secret? | Owner / provider | When configured | Rotation |
|---|---|---|---|---|
| `DB_HOST` | Not secret (infra topology) | DigitalOcean (or selected DB provider) | At database provisioning | On provider migration only |
| `DB_PORT` | No | Provider default | At provisioning | Never |
| `DB_NAME` | No | Operator-chosen | At provisioning | Never (renaming a live DB is disruptive) |
| `DB_USERNAME` | Yes | Operator-created application role (not the provider's admin role) | At provisioning | On suspected compromise, or per a routine schedule (e.g. annually) |
| `DB_PASSWORD` | Yes | Operator-generated, stored in the host secrets mechanism | At provisioning | Same as username; see "Secret Rotation Plan" below |

## Mail (SMTP)

| Variable | Secret? | Owner / provider | When configured | Rotation |
|---|---|---|---|---|
| `MAIL_HOST` | No | Amazon SES (recommended) | At SES setup | Only if provider changes |
| `MAIL_PORT` | No | SES-documented port | At SES setup | Never |
| `MAIL_USERNAME` | Yes | SES SMTP credential (IAM-scoped, not the AWS account root) | At SES setup | Per a routine schedule; immediately on suspected compromise |
| `MAIL_PASSWORD` | Yes | Same | Same | Same |

## Public URL / CORS

| Variable | Secret? | Owner / provider | When configured | Rotation |
|---|---|---|---|---|
| `APP_PUBLIC_URL` / `FRONTEND_URL` | No | The final chosen domain (`BUSINESS_DECISIONS_REQUIRED.md`) | Once the domain is registered and DNS resolves | Only on domain change |

## Admin bootstrap

| Variable | Secret? | Owner / provider | When configured | Rotation |
|---|---|---|---|---|
| `APP_ADMIN_BOOTSTRAP_ENABLED` | No (boolean) | Operator | Set `true` for exactly the first production startup only | Set back to `false` immediately after — see "Admin Bootstrap Final Pass" below |
| `ADMIN_BOOTSTRAP_EMAIL` | Sensitive (PII), not classified SECRET | Operator | Same single startup | Unset after use |
| `ADMIN_BOOTSTRAP_PASSWORD` | Yes | Operator-generated, one-time | Same single startup | Unset/rotated immediately after first login (the first real ADMIN should change their own password on first login) |

## Release / image identity

| Variable | Secret? | Owner / provider | When configured | Rotation |
|---|---|---|---|---|
| `BUILD_COMMIT` | No | CI (automatic, `git rev-parse HEAD`) | Every build | N/A |
| `IMAGE_TAG` | No | Operator/CI, always an explicit SHA or version, never `latest` in a real release | Every deploy | Every release |
| `BACKEND_IMAGE` / `FRONTEND_IMAGE` | No | `ghcr.io/<org>/foreigner-warsaw-{backend,frontend}` | Once GHCR is confirmed as the registry | Only if registry changes |
| `HTTP_PORT` | No | Deploy-time, defaults `8080` | At deploy | Rarely |

## New for L1 — deployment credentials

| Item | Secret? | Owner / provider | When configured | Rotation |
|---|---|---|---|---|
| GHCR deploy token (pull-only, least-privilege) | Yes | GitHub (a fine-grained PAT or GitHub Actions OIDC, not a broad classic PAT) | Once the real deploy pipeline runs against a real host | Per a routine schedule; immediately on suspected compromise |
| Deploy-host SSH key (if a VPS is used) | Yes | Operator-generated, key-only auth | At host provisioning | Immediately on suspected compromise; periodically otherwise |
| DigitalOcean / provider API token (if used for automation) | Yes | Provider account | Only if infrastructure automation is added | Per a routine schedule |
| Sentry DSN (if adopted) | Not classified SECRET (a DSN is meant to be embedded in client code) but should still not be logged unnecessarily | Sentry | If/when adopted | Only on project reset |

## Secret rotation plan

- **DB password**: create the new password at the provider, update the deployed
  secret, restart the backend (Hikari reconnects on next pool refresh) — no
  application code change needed, since the credential is entirely environment-driven.
- **SMTP credentials**: same pattern via SES's own IAM credential rotation.
- **Deploy token**: revoke the old GHCR/provider token, issue a new least-privilege
  one, update the CI/deploy-host secret store.
- **Admin bootstrap credential**: not rotated — it is one-time-use by design
  (`AdminBootstrapRunner` is a permanent no-op once any `ADMIN` exists); the real
  mitigation is disabling and unsetting it immediately after first use, not rotating
  it.
- **Monitoring token** (if Sentry or similar is adopted): rotate via that provider's
  own token-management UI, same pattern as the deploy token.

## Admin bootstrap production procedure (finalized from Phase 13)

```text
1. Deploy with APP_ADMIN_BOOTSTRAP_ENABLED=true, real ADMIN_BOOTSTRAP_EMAIL/
   ADMIN_BOOTSTRAP_PASSWORD set for this one startup only.
2. Confirm the log line ("Admin bootstrap: created the first ADMIN account for ...").
3. Log in as that account through the real production UI.
4. Change the password immediately (the bootstrap password should be treated as
   already-potentially-exposed the moment it existed in an env file/CI secret).
5. Redeploy with APP_ADMIN_BOOTSTRAP_ENABLED=false and both other variables unset.
6. Confirm AdminBootstrapRunner is now a permanent no-op (it already is, by design,
   once any ADMIN row exists — this step just confirms the config hygiene, not new
   behavior).
```

No permanent bootstrap secret exists at any point after step 5.

## Staging vs. production separation

Staging never uses production `DB_*`/`MAIL_*` credentials — a separate database
instance and a sandboxed/test-mode email configuration (`EMAIL_PRODUCTION.md`'s own
"staging must not email real users" rule, unchanged). Cookie domains are kept
distinct (`staging.<domain>` vs. `<domain>`) so a staging session can never be
confused with a production one — same-origin architecture (CORS/CSRF) is preserved
in both environments independently, never shared across them.
