import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Mirrors the backend's RecommendationRunStatus (Phase 7) - RUNNING never reaches the frontend
 * (the analyze endpoint responds synchronously, brief §78), kept here only for type completeness. */
export type RecommendationRunStatus = 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';

/** Mirrors the backend's RecommendationType - never a percentage or confidence score. */
export type RecommendationType =
  | 'PRIMARY_MATCH'
  | 'POSSIBLE_ALTERNATIVE'
  | 'MORE_INFORMATION_REQUIRED'
  | 'NOT_APPLICABLE'
  | 'UNAVAILABLE_FOR_ANALYSIS';

export type RecommendationReasonType =
  | 'MATCHED_CONDITION'
  | 'FAILED_CONDITION'
  | 'MISSING_INFORMATION'
  | 'EXCLUSION'
  | 'ALTERNATIVE_PATH'
  | 'PROCEDURE_PRIORITY'
  | 'ANALYSIS_ERROR';

export interface RecommendationReason {
  reasonType: RecommendationReasonType;
  reasonCode: string;
  messageKey: string | null;
  factCode: string | null;
}

export interface OfficialSourceRef {
  authority: string | null;
  title: string;
  url: string;
  role: string;
  lastVerifiedAt: string | null;
}

export interface Recommendation {
  procedureCode: string;
  procedureTitle: string;
  recommendationType: RecommendationType;
  rank: number;
  reasons: RecommendationReason[];
  missingFacts: string[];
  officialSources: OfficialSourceRef[];
}

export interface RecommendationRun {
  id: string;
  assessmentId: string;
  evaluationDate: string;
  status: RecommendationRunStatus;
  recommendationEngineVersion: string;
  ruleEngineVersion: string;
  createdAt: string;
  completedAt: string | null;
  recommendations: Recommendation[];
}

export interface RecommendationRunSummary {
  id: string;
  evaluationDate: string;
  status: RecommendationRunStatus;
  createdAt: string;
  completedAt: string | null;
  recommendationCount: number;
  primaryMatchCount: number;
}

/**
 * Thin HTTP client for the Phase 7 recommendation engine - never evaluates a rule or classifies a
 * recommendation itself (brief §14/§102), just reflects whatever the backend already decided.
 */
@Injectable({ providedIn: 'root' })
export class RecommendationService {
  private readonly http = inject(HttpClient);
  private readonly assessmentsBase = `${environment.apiBaseUrl}/assessments`;
  private readonly runsBase = `${environment.apiBaseUrl}/recommendation-runs`;

  /** Creates a new immutable RecommendationRun (brief §39) - never overwrites a prior one. */
  analyze(assessmentId: string): Observable<RecommendationRun> {
    return this.http.post<RecommendationRun>(`${this.assessmentsBase}/${assessmentId}/recommendation-runs`, {});
  }

  getLatest(assessmentId: string): Observable<RecommendationRun> {
    return this.http.get<RecommendationRun>(`${this.assessmentsBase}/${assessmentId}/recommendations/latest`);
  }

  getHistory(assessmentId: string): Observable<RecommendationRunSummary[]> {
    return this.http.get<RecommendationRunSummary[]>(`${this.assessmentsBase}/${assessmentId}/recommendation-runs`);
  }

  getRun(runId: string): Observable<RecommendationRun> {
    return this.http.get<RecommendationRun>(`${this.runsBase}/${runId}`);
  }
}
