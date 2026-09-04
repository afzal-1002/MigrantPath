# Launch Enablement Checklist

Post-MVP Milestone L1. Statuses: `DONE`, `READY_TO_CONFIGURE` (a real plan exists,
needs only approval + execution), `USER_DECISION_REQUIRED`, `EXTERNAL_REVIEW_REQUIRED`,
`BLOCKED`.

## Business Identity
| Item | Status |
|---|---|
| Final product/brand name | `USER_DECISION_REQUIRED` |
| Operator/company legal identity | `USER_DECISION_REQUIRED` |
| Business address (if legally required) | `USER_DECISION_REQUIRED` |

## Domain
| Item | Status |
|---|---|
| Domain-selection criteria documented | `DONE` (`PROVIDER_COMPARISON.md`) |
| Actual domain name chosen | `USER_DECISION_REQUIRED` (depends on brand name) |
| Domain registered | `BLOCKED` (depends on the above; not purchased this milestone) |

## DNS / TLS
| Item | Status |
|---|---|
| DNS strategy documented | `DONE` (provider-neutral / Cloudflare optional, `PRODUCTION_ARCHITECTURE_DECISION.md`) |
| TLS strategy documented | `DONE` (Let's Encrypt via reverse proxy, or platform-managed) |
| Real DNS zone/TLS certificate | `BLOCKED` (needs a real domain first) |

## Hosting
| Item | Status |
|---|---|
| Options researched, current pricing | `DONE` (`PROVIDER_COMPARISON.md`) |
| Primary recommendation | `READY_TO_CONFIGURE` (DigitalOcean Droplet + Managed PostgreSQL) |
| Secondary recommendation | `READY_TO_CONFIGURE` (Hetzner Cloud VPS, self-hosted DB) |
| Account created / provisioned | `BLOCKED` (not performed — needs `USER_DECISION_REQUIRED` approval first) |

## Database
| Item | Status |
|---|---|
| PostgreSQL 18 provider availability confirmed | `DONE` (DigitalOcean, AWS RDS, Aiven all confirmed) |
| Provider recommendation | `READY_TO_CONFIGURE` (DigitalOcean Managed PostgreSQL) |
| Provisioned instance | `BLOCKED` |

## Email
| Item | Status |
|---|---|
| Providers compared | `DONE` |
| Primary recommendation | `READY_TO_CONFIGURE` (Amazon SES) |
| SPF/DKIM/DMARC plan | `READY_TO_CONFIGURE` — SPF + DKIM configured with the sending domain at SES setup; DMARC starts at `p=none` (monitor-only, collecting real delivery reports) and is only tightened to `p=quarantine`/`p=reject` after observing several weeks of clean reports — never starts strict, per brief §31/§109's own explicit warning against a destructive initial policy |
| Sender aliases decided | `USER_DECISION_REQUIRED` |
| Real SMTP credentials configured | `BLOCKED` |

## Secrets
| Item | Status |
|---|---|
| Strategy finalized | `DONE` (`PRODUCTION_CONFIGURATION_PLAN.md`) |
| Rotation plan documented | `DONE` |
| Real secrets generated | `BLOCKED` (needs real provider accounts first) |

## Backups
| Item | Status |
|---|---|
| Strategy linked to DB provider choice | `DONE` (DigitalOcean Managed PostgreSQL includes automated backups + PITR) |
| Real backup verified in production | `BLOCKED` (needs a real deployed database) |

## Monitoring
| Item | Status |
|---|---|
| Minimum production observability defined | `DONE` — availability (readiness probe), logs (structured JSON, already real), error visibility (structured `ERROR` logs + `rule.evaluation.error`/`recommendation.failed` metrics, already real), a notification channel (alert delivery, see below), and backup-failure visibility (the selected DB provider's own backup-status dashboard) together satisfy brief §43's minimum bar without a full enterprise stack |
| Error-tracking decision | `USER_DECISION_REQUIRED` (`USE NOW` vs. `DEFER` — recommendation: `DEFER` is acceptable, structured logs + metrics + correlation IDs are sufficient for a small MVP launch; adopt Sentry when budget/operational maturity justifies it) |
| Uptime monitor selected | `USER_DECISION_REQUIRED` (low-stakes; any credible free-tier option is fine) |

## Alerts
| Item | Status |
|---|---|
| Alert catalogue exists | `DONE` (Canonical Phase 14, `ALERTS.md`) |
| Real delivery channel selected | `USER_DECISION_REQUIRED` (recommendation: start with email — simplest, zero new provider) |

## Legal Review
| Item | Status |
|---|---|
| Handoff package prepared | `DONE` (`LEGAL_REVIEW_HANDOFF.md`) |
| Reviewer engaged | `EXTERNAL_REVIEW_REQUIRED` |
| Privacy Policy/Terms/Disclaimer/Cookie Policy finalized | `EXTERNAL_REVIEW_REQUIRED` |

## Privacy
| Item | Status |
|---|---|
| Technical controls (export/deletion/session invalidation) | `DONE`, verified against production images (Phase 15) |
| Processor/DPA review | `EXTERNAL_REVIEW_REQUIRED` (once providers are actually selected) |
| Lawful-basis/retention-wording/minors-policy/special-category classification | `EXTERNAL_REVIEW_REQUIRED` |

## Support
| Item | Status |
|---|---|
| Required aliases identified | `DONE` (`support@`/`privacy@`/`security@`) |
| Real mailboxes configured | `USER_DECISION_REQUIRED` (depends on domain) |

## Legal Content
| Item | Status |
|---|---|
| Provisioning problem identified and solved at design level | `DONE` (`LEGAL_CONTENT_PROVISIONING.md`) |
| Sanitization script written and verified | `BLOCKED` (a real, scoped engineering task not performed this milestone) |
| Production content actually provisioned | `BLOCKED` (depends on the above and a real production database existing) |

## Staging
| Item | Status |
|---|---|
| Strategy recommended | `DONE` (ephemeral/on-demand, `PRODUCTION_COST_ESTIMATE.md`) |
| Real staging environment stood up | `BLOCKED` — this is Milestone L2's own scope, not L1's |

## Production
| Item | Status |
|---|---|
| Technical release candidate | `DONE` (`0.1.0-rc.1`, GO per Phase 15) |
| Real production deployment | `BLOCKED` — not performed this milestone, by explicit instruction |
