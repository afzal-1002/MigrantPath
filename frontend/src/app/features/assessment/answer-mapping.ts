import { AnswerRequest, AnswerValue, AnswerType } from '../../core/services/assessment.service';

/** One question's editable form state - a single raw JS value (shape depends on `answerType`,
 * see {@link valueFromAnswer}) plus the "I'm not sure" flag, kept separate so the main control
 * can be disabled without losing its last-entered value if the user un-checks "not sure" again. */
export interface QuestionFormValue {
  value: unknown;
  unsure: boolean;
}

/** The empty/blank form value for a question with no existing answer. */
export function blankValue(answerType: AnswerType): unknown {
  return answerType === 'MULTI_SELECT' ? [] : answerType === 'BOOLEAN' ? null : '';
}

/** Converts a persisted {@link AnswerValue} back into the raw form value the renderer's control
 * expects - the inverse of {@link toAnswerRequest}. */
export function valueFromAnswer(answerType: AnswerType, answer: AnswerValue | null): QuestionFormValue {
  if (!answer) {
    return { value: blankValue(answerType), unsure: false };
  }
  if (answer.unsure) {
    return { value: blankValue(answerType), unsure: true };
  }
  switch (answerType) {
    case 'BOOLEAN':
      return { value: answer.booleanValue, unsure: false };
    case 'MULTI_SELECT':
      return { value: answer.selectedOptionCodes ?? [], unsure: false };
    case 'INTEGER':
      return { value: answer.integerValue, unsure: false };
    case 'DECIMAL':
      return { value: answer.decimalValue, unsure: false };
    case 'DATE':
      return { value: answer.dateValue, unsure: false };
    case 'COUNTRY':
    case 'REGION':
    case 'CITY':
    case 'DISTRICT':
    case 'SINGLE_SELECT':
      return { value: answer.referenceCode, unsure: false };
    case 'TEXT':
    default:
      return { value: answer.stringValue, unsure: false };
  }
}

/** Converts the renderer's raw form value into the typed {@link AnswerRequest} the backend
 * expects - never a stringified catch-all (brief §25). */
export function toAnswerRequest(answerType: AnswerType, formValue: QuestionFormValue): AnswerRequest {
  if (formValue.unsure) {
    return { unsure: true };
  }
  const value = formValue.value;
  switch (answerType) {
    case 'BOOLEAN':
      return { booleanValue: value as boolean };
    case 'MULTI_SELECT':
      return { selectedOptionCodes: value as string[] };
    case 'INTEGER':
      return { integerValue: value === '' || value === null ? null : Number(value) };
    case 'DECIMAL':
      return { decimalValue: value === '' || value === null ? null : Number(value) };
    case 'DATE':
      return { dateValue: (value as string) || null };
    case 'COUNTRY':
    case 'REGION':
    case 'CITY':
    case 'DISTRICT':
    case 'SINGLE_SELECT':
      return { referenceCode: (value as string) || null };
    case 'TEXT':
    default:
      return { stringValue: (value as string) || null };
  }
}

/** A question is "answered" for this session's client-side Next/required-field check when it
 * has a non-blank value or is marked unsure - the backend's own completion check (brief §36) is
 * still authoritative; this only drives the wizard's own inline validation messages. */
export function isBlank(answerType: AnswerType, formValue: QuestionFormValue): boolean {
  if (formValue.unsure) {
    return false;
  }
  const value = formValue.value;
  if (answerType === 'MULTI_SELECT') {
    return !Array.isArray(value) || value.length === 0;
  }
  if (answerType === 'BOOLEAN') {
    return value === null || value === undefined;
  }
  return value === null || value === undefined || value === '';
}
