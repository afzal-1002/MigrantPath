# ADR-009: A custom deterministic condition-tree evaluator, not Drools/SpEL

Status: Accepted — 2026-09-03

## Context

ADR-003 already decided *that* eligibility is decided by a deterministic rules engine,
not an LLM. Phase 6 had to decide *how* that engine is actually built: what stores a
rule's logic, what walks it, and what vocabulary an admin (eventually) authors it in.

Two off-the-shelf options were available and explicitly considered:

- **A production rule engine (e.g. Drools).** Battle-tested, but brings a large
  dependency, its own DSL/compilation model, and a maintenance/learning surface far
  beyond what a few dozen legal rules with `ALL`/`ANY`/`NOT` + typed comparisons need.
- **Admin-authored expressions (SpEL or similar), stored as text in the database and
  evaluated at runtime.** Flexible, but lets a content author (not a developer) write
  arbitrary executable logic against application objects — a security and
  auditability risk (an expression can do things a condition tree structurally cannot),
  and no better for legal traceability than a well-designed data model.

## Decision

Build a small, purpose-built evaluator over a **closed, non-executable JSON condition
tree** (`ConditionNode`: `AllNode` / `AnyNode` / `NotNode` / `LeafCondition`, a sealed
interface), with:

- **A fixed, closed operator vocabulary** (`ComparisonOperator`) — no operator is ever
  admin-defined; adding one requires a code change and a matching entry in
  [OPERATOR_SEMANTICS.md](../../rules/OPERATOR_SEMANTICS.md).
- **A structural parser** (`ConditionTreeParser`) that rejects anything that isn't
  exactly one of `all`/`any`/`not`/leaf, an unknown operator name, or excessive nesting
  (depth > 10) — independent of any database, reusable by tests and tooling.
- **A semantic validator** (`ConditionTreeValidator`) that additionally rejects an
  unknown fact, an operator not valid for that fact's type, an unknown threshold code, or
  an unknown country-group code — run once, at publish time (brief §23/§65), so the
  runtime evaluator almost never encounters a broken rule.
- **A four-state result model** (`PASS`/`FAIL`/`MISSING`/`ERROR` per condition,
  `SATISFIED`/`NOT_SATISFIED`/`INDETERMINATE`/`ERROR` per rule) — see
  [OPERATOR_SEMANTICS.md](../../rules/OPERATOR_SEMANTICS.md) for the exact `ALL`/`ANY`/`NOT`
  combination table. `MISSING` is not an afterthought: it is why a condition tree, not a
  boolean expression, was chosen as the representation — a boolean expression language
  (SpEL included) has no native third state for "not yet known."
- **Reuse over duplication**: the actual typed comparison (once both sides are known and
  present) is delegated to Phase 5's existing `ConditionEvaluator` — the same evaluator
  `QuestionDependency` already used for branching — extended with the handful of
  operators Phase 6 needed (`BETWEEN`, `DATE_BEFORE_OR_EQUAL`, `DATE_AFTER_OR_EQUAL`).
  `RuleEvaluator` owns only the layer `ConditionEvaluator` never needed: the
  MISSING-short-circuit, threshold/country-group resolution, and ERROR wrapping.

**No JavaScript, SpEL, SQL fragment, or Groovy is ever accepted or evaluated as a
condition.** A condition tree is data; only the fixed set of Java classes above ever
interprets it.

**Drools was not introduced.** The concrete cost (a large dependency, a second
DSL/compilation model to maintain and explain, a mismatch with the versioned-JSONB
storage model already proven for `QuestionDependency`) was not justified by any concrete
benefit this phase's rule volume and logic shape (nested boolean composition + typed
leaf comparisons) needed. This can be revisited if a genuinely Drools-shaped problem
appears (e.g. real-time high-volume rule chaining), but that is not this problem.

## Consequences

- Adding a new operator or node type is a Java change (reviewed, tested, documented),
  never a runtime-configurable one — deliberately narrower than a general expression
  engine, in exchange for auditability: every possible condition shape is enumerable.
- The engine has no external dependency beyond what Phase 4/5 already brought in
  (Jackson for JSONB, Spring, the shared `ComparisonOperator`/`ConditionEvaluator`).
- Publish-time validation (`ConditionTreeValidator`) is what keeps the runtime evaluator
  simple — it can assume a `RuleVersion` reaching `PUBLISHED` already has a structurally
  and semantically valid tree, and treats any exception it still encounters as `ERROR`,
  never as a silent `FAIL` (brief §64/§118).
- A rule composed of other rules' outcomes (brief §24's "rule-to-rule dependency") is
  deliberately **not** supported in Phase 6 — each `RuleVersion` is a standalone tree.
  `RuleOutcome` exists as a placeholder table for this (DATABASE.md §5) but is unused;
  building a dependency graph before a concrete need exists was judged premature.

See [ARCHITECTURE.md §7](../ARCHITECTURE.md), [DATABASE.md §5](../../database/DATABASE.md),
and [docs/rules/](../../rules/) for the concrete schema/semantics this decision produced.
