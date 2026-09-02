import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Mirrors backend AnswerType/QuestionType (docs/product/QUESTION_CODES.md). */
export type AnswerType =
  | 'BOOLEAN'
  | 'SINGLE_SELECT'
  | 'MULTI_SELECT'
  | 'TEXT'
  | 'INTEGER'
  | 'DECIMAL'
  | 'DATE'
  | 'COUNTRY'
  | 'REGION'
  | 'CITY'
  | 'DISTRICT';

export type OptionSource = 'STATIC' | 'REFERENCE_COUNTRY' | 'REFERENCE_REGION' | 'REFERENCE_CITY' | 'REFERENCE_DISTRICT';

export type AssessmentStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED' | 'SUPERSEDED';

export interface QuestionOption {
  code: string;
  label: string;
  description: string | null;
}

/** Exactly one field (or `selectedOptionCodes`) is set, matching the question's `answerType` -
 * mirrors the backend's typed-column AssessmentAnswer, never a stringified catch-all. */
export interface AnswerValue {
  stringValue: string | null;
  booleanValue: boolean | null;
  integerValue: number | null;
  decimalValue: number | null;
  dateValue: string | null;
  referenceCode: string | null;
  selectedOptionCodes: string[] | null;
  unsure: boolean;
}

export type AnswerRequest = Partial<Omit<AnswerValue, 'unsure'>> & { unsure?: boolean };

export interface QuestionDefinition {
  questionnaireQuestionId: string;
  questionCode: string;
  fieldKey: string;
  sectionCode: string;
  label: string;
  helpText: string | null;
  required: boolean;
  sortOrder: number;
  answerType: AnswerType;
  optionSource: OptionSource;
  allowUnsure: boolean;
  options: QuestionOption[];
  answer: AnswerValue | null;
}

export interface Section {
  code: string;
  title: string;
  sortOrder: number;
}

export interface MissingQuestion {
  questionCode: string;
  label: string;
  sectionCode: string;
}

export interface AssessmentDetail {
  id: string;
  status: AssessmentStatus;
  questionnaireCode: string;
  questionnaireVersionId: string;
  startedAt: string;
  completedAt: string | null;
  progressPercent: number;
  sections: Section[];
  questions: QuestionDefinition[];
  missingRequiredQuestions: MissingQuestion[];
}

export interface AssessmentSummary {
  id: string;
  status: AssessmentStatus;
  questionnaireCode: string;
  startedAt: string;
  completedAt: string | null;
}

/**
 * The wizard's only source of truth for questionnaire structure/branching (brief §30) - the
 * backend, not this service, decides what's visible/required/missing. This service never
 * evaluates a `QuestionDependency` itself; every method just reflects whatever the latest
 * `AssessmentDetail` response says.
 */
@Injectable({ providedIn: 'root' })
export class AssessmentService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/assessments`;

  /** Starts a new assessment, or resumes the caller's existing in-progress one (brief §33). */
  start(): Observable<AssessmentDetail> {
    return this.http.post<AssessmentDetail>(this.base, {});
  }

  list(): Observable<AssessmentSummary[]> {
    return this.http.get<AssessmentSummary[]>(this.base);
  }

  get(id: string): Observable<AssessmentDetail> {
    return this.http.get<AssessmentDetail>(`${this.base}/${id}`);
  }

  saveAnswer(id: string, questionCode: string, request: AnswerRequest): Observable<AssessmentDetail> {
    return this.http.put<AssessmentDetail>(`${this.base}/${id}/answers/${questionCode}`, request);
  }

  complete(id: string): Observable<AssessmentDetail> {
    return this.http.post<AssessmentDetail>(`${this.base}/${id}/complete`, {});
  }

  restart(id: string): Observable<AssessmentDetail> {
    return this.http.post<AssessmentDetail>(`${this.base}/${id}/restart`, {});
  }
}
