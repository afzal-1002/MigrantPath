package com.foreignerwarsaw.questionnaire.core;

/**
 * Well-known {@link Questionnaire#getCode()} values. Not legal/administrative content (CLAUDE.md's
 * "never hard-code legal content" rule does not apply to an identity/routing code, same as {@code
 * ProcedureController} resolving by path-variable code) - V1 scope is a single Warsaw-wide
 * questionnaire (brief §41), so the "active questionnaire" endpoints resolve this one constant
 * rather than taking a code parameter nothing in the product yet needs.
 */
public final class QuestionnaireCodes {

  public static final String WARSAW_GENERAL_ASSESSMENT = "WARSAW_GENERAL_ASSESSMENT";

  private QuestionnaireCodes() {}
}
