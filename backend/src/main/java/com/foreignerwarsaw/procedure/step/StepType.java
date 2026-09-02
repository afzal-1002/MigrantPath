package com.foreignerwarsaw.procedure.step;

/**
 * Mirrors {@code step_versions.step_type}'s CHECK constraint (V27, brief §13). Not every procedure
 * has every step type (brief: "do not assume every procedure has the same steps") - this is just
 * the closed vocabulary a step's type is drawn from.
 */
public enum StepType {
  INFORMATION,
  PREPARATION,
  DOCUMENT,
  PAYMENT,
  ONLINE_SUBMISSION,
  IN_PERSON_SUBMISSION,
  APPOINTMENT,
  BIOMETRICS,
  WAITING,
  ADDITIONAL_DOCUMENTS,
  DECISION,
  COLLECTION,
  OTHER
}
