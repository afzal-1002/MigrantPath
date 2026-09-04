# DNS and TLS

Status: **DOCUMENTED, NOT EXECUTED.** No real domain exists for this project yet (brief
§71/§124/§188 - hosting provider is also `NOT SELECTED`), so nothing below has been run
against a real DNS zone or issued a real certificate. This document is provider-neutral
guidance for whoever performs the first real deployment, not a record of something
already done.

## Domain shape

Two subdomains (or two domains), matching the environment model
(`ENVIRONMENTS.md`):

```text
staging.example.com     -> the staging deployment (docs/operations/STAGING.md)
app.example.com         -> production
```

(`example.com` is a placeholder throughout - never a real, undeclared domain.)

## DNS records (conceptual, any provider)

- An `A`/`AAAA` (or a provider-specific alias, e.g. a cloud load balancer's own
  hostname via `CNAME`/`ALIAS`) record for each subdomain above, pointed at the host or
  load balancer running the `frontend` (reverse-proxy) container - never directly at
  the `backend` container, which has no publicly-mapped port at all (ADR-013).
- No wildcard record needed - this application has exactly two public hostnames.
- TTL: a short value (e.g. 300s) while a deployment target is still being finalized,
  raised once stable - keeps a future cutover/rollback fast.

## TLS

This repository deliberately does not bundle a certificate-issuance tool (ADR-013) -
TLS termination is a hosting-platform/reverse-proxy concern that sits **in front of**
the two application containers, not inside them. Concrete options, any of which work
unmodified with this topology:

1. **A cloud load balancer/managed ingress with automatic TLS** (most managed
   container platforms, e.g. a cloud provider's own HTTPS load balancer) - terminates
   TLS before traffic ever reaches the `frontend` container; the container itself keeps
   listening on plain HTTP (`nginx-unprivileged`, port 8080, unprivileged by design -
   `frontend/Dockerfile`).
2. **A TLS-terminating reverse proxy on the same host, in front of the two containers**
   (e.g. Caddy or a certbot-managed nginx) - Let's Encrypt via that proxy's own ACME
   automation is the common self-hosted choice; renewal is that proxy's own concern,
   not this repository's.

Either way: `server.forward-headers-strategy: framework` (already set in
`application-staging.yml`/`application-production.yml`) makes the backend correctly see
`https` via the trusted `X-Forwarded-Proto` header the TLS-terminating layer sets - see
"Trusted proxy" below for why this is only safe in this exact topology.

## HTTPS redirect

Once a real TLS terminator exists, configure it to redirect plain HTTP → HTTPS (a
standard reverse-proxy/load-balancer setting, not application code). **Never** test this
locally by weakening the application's own cookie `Secure` behavior (brief §72) - this
phase's own local verification (`PHASE_13_REPORT.md`) ran over plain HTTP deliberately
and confirmed the session cookie correctly omits `Secure` in that case (it reflects the
real, honest `X-Forwarded-Proto` the proxy sends, `http`, in a no-TLS local rig) -
proof the mechanism works correctly in both directions, not a workaround.

## Trusted proxy (brief §73)

`forward-headers-strategy: framework` trusts whatever `X-Forwarded-Proto`/
`X-Forwarded-For` header arrives at the backend - safe **only** because the backend
has no publicly-mapped port in this topology (ADR-013) and is reachable exclusively
through the `frontend` container's nginx, which is the only component that should ever
set those headers from a real client's connection. Whoever provisions the real host is
responsible for ensuring nothing else (a stray public port, a misconfigured load
balancer forwarding raw client headers) can reach the backend directly and spoof them -
see "Firewall" below and `ARCHITECTURE.md §13`.

## Firewall (brief §74)

Minimal ingress on the host running the two containers:

```text
80   (HTTP, redirected to 443 by the TLS terminator)
443  (HTTPS)
22   (SSH, restricted to the operator's own IP/a bastion - deployment/maintenance only)
```

Postgres (self-hosted-db profile) and the backend container have no `ports:` mapping
in `infra/docker-compose.prod.yml` at all - not "closed by firewall rule" but
structurally unreachable from outside the Docker network in the first place.

## Renewal

Automatic with either option above (a managed load balancer's own certificate renewal,
or a self-hosted ACME client's own renewal timer) - no action needed in this
repository. Monitor certificate expiry via the hosting platform's own alerting (Phase
14's scope, not built here).

## Rollback considerations

A DNS/TLS misconfiguration (wrong record, expired/misissued certificate) is
independent of an application code rollback (`ROLLBACK.md`) - reverting to a previous
image tag does not fix a DNS/TLS problem, and vice versa. Keep the two failure modes
separate when diagnosing a real incident.
