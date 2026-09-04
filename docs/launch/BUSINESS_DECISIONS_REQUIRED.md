# Business Decisions Required

Post-MVP Milestone L1. Only items that genuinely require user/business judgment —
nothing here has been decided or invented on this project's behalf.

1. **Final product/brand name.** The codebase currently uses "Foreigner Warsaw"
   throughout; earlier product framing also referenced "Foreigner Assistant Poland."
   The final public-facing name affects the domain, the legal pages, the email sender
   identity, and page titles/metadata. Not renamed in the codebase pending this
   decision (renaming prematurely would be real, disruptive churn for no benefit
   until the name is actually final).
2. **Domain name.** Depends on (1). See `PROVIDER_COMPARISON.md`'s domain-selection
   criteria (concise, trustworthy, not government-looking, Poland-relevant,
   `.pl`/`.com`) — no specific name is recommended or reserved.
3. **Operator/company legal identity.** Individual, JDG (sole proprietorship), sp. z
   o.o., or another structure — determines what the Terms of Service/Privacy Policy
   must legally state about who is providing the service, and affects tax/VAT
   treatment of any provider costs.
4. **Business address**, if the chosen legal identity requires one to be published
   (varies by structure and by what Polish law requires for this kind of service —
   confirm with the legal reviewer, not assumed here).
5. **Support email address** (`support@<domain>`).
6. **Privacy contact email address** (`privacy@<domain>`) — the Privacy Policy's
   "Contact" section is currently honestly blank pending this.
7. **Security contact email address** (`security@<domain>`) — may initially be the
   same mailbox as support/privacy; a real decision either way.
8. **Hosting budget approval.** `PRODUCTION_COST_ESTIMATE.md`'s three tiers
   (~€25–30/mo minimum, ~$39–68/mo recommended, ~$115–140/mo higher-reliability) —
   which tier, and any adjustment to it.
9. **Selected providers**, once this document's recommendations are reviewed:
   hosting/compute, managed PostgreSQL, transactional email, error tracking (or
   explicitly deferred), DNS.
10. **Legal-review owner.** Who actually engages a lawyer/privacy professional to
    review the Privacy Policy/Terms/Disclaimer/Cookie Policy — and their availability/
    timeline, which gates the "Public Launch: CONDITIONAL GO" resolution in
    `FINAL_GO_NO_GO.md`.
11. **Launch date target** (even approximate) — useful for sequencing the legal
    review, provider setup, and legal-content provisioning work realistically, not to
    create artificial pressure on any of them.
12. **Whether to adopt error tracking (Sentry) at launch, or defer it** — a real cost/
    operational-risk trade-off (`PRODUCTION_COST_ESTIMATE.md`, "Error Tracking
    Decision" in `LAUNCH_ENABLEMENT_CHECKLIST.md`), not a technical question this
    codebase can answer for itself.
13. **Which staging strategy to actually use** — this document recommends ephemeral/
    on-demand staging (`PRODUCTION_COST_ESTIMATE.md`) as the cost-appropriate default
    for a solo/small-team MVP; confirm or override.

None of the above has been decided, invented, or defaulted on this project's behalf.
