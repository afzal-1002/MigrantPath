package com.foreignerwarsaw.procedure.document;

/**
 * Mirrors {@code document_requirement_versions.requirement_type}'s CHECK constraint (V28, brief
 * §15/§16). {@code CONDITIONAL} is the forward-compatible placeholder for "a future Phase 6 Rule
 * decides whether this applies to a given user" - deliberately without a foreign key to a Rule
 * table that doesn't exist yet. Phase 4 never evaluates which condition applies to which user.
 */
public enum RequirementType {
  DEFAULT_REQUIRED,
  CONDITIONAL,
  INFORMATIONAL
}
