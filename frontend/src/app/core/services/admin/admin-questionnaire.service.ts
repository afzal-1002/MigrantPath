import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminReview } from './admin-procedure.service';

export interface AdminQuestionnaireSummary {
  code: string;
  activeVersionNumber: number | null;
  latestVersionNumber: number | null;
  latestVersionStatus: string | null;
}

export interface AdminQuestionSummary {
  questionCode: string;
  sectionCode: string;
  label: string;
  required: boolean;
  sortOrder: number;
  questionType: string;
}

export interface AdminQuestionnaireVersionDetail {
  id: string;
  questionnaireCode: string;
  versionNumber: number;
  title: string;
  description: string | null;
  status: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  lockVersion: number;
  createdByEmail: string | null;
  submittedByEmail: string | null;
  approvedByEmail: string | null;
  publishedByEmail: string | null;
  publishedAt: string | null;
  questions: AdminQuestionSummary[];
}

export interface QuestionnaireImpact {
  count: number;
  description: string;
}

/**
 * Admin client for Questionnaires (brief §94) - version lifecycle + read-only question listing
 * only; deep question/dependency editing is a documented scope cut (see PHASE_9_REPORT.md).
 */
@Injectable({ providedIn: 'root' })
export class AdminQuestionnaireService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/questionnaires`;

  list(): Observable<AdminQuestionnaireSummary[]> {
    return this.http.get<AdminQuestionnaireSummary[]>(this.base);
  }

  versionHistory(code: string): Observable<AdminQuestionnaireVersionDetail[]> {
    return this.http.get<AdminQuestionnaireVersionDetail[]>(`${this.base}/${code}`);
  }

  copyVersion(
    code: string,
    versionNumber: number,
    request: { title: string; description?: string },
  ): Observable<AdminQuestionnaireVersionDetail> {
    return this.http.post<AdminQuestionnaireVersionDetail>(
      `${this.base}/${code}/versions/${versionNumber}/copy`,
      request,
    );
  }

  submit(code: string, versionNumber: number): Observable<AdminQuestionnaireVersionDetail> {
    return this.http.post<AdminQuestionnaireVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/submit`, {});
  }

  approve(code: string, versionNumber: number, comment?: string): Observable<AdminQuestionnaireVersionDetail> {
    return this.http.post<AdminQuestionnaireVersionDetail>(
      `${this.base}/${code}/versions/${versionNumber}/approve`,
      { comment },
    );
  }

  requestChanges(code: string, versionNumber: number, comment: string): Observable<AdminQuestionnaireVersionDetail> {
    return this.http.post<AdminQuestionnaireVersionDetail>(
      `${this.base}/${code}/versions/${versionNumber}/request-changes`,
      { comment },
    );
  }

  publish(code: string, versionNumber: number, effectiveFrom: string): Observable<AdminQuestionnaireVersionDetail> {
    return this.http.post<AdminQuestionnaireVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/publish`, {
      effectiveFrom,
    });
  }

  archive(code: string, versionNumber: number): Observable<AdminQuestionnaireVersionDetail> {
    return this.http.post<AdminQuestionnaireVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/archive`, {});
  }

  impact(code: string, versionNumber: number): Observable<QuestionnaireImpact> {
    return this.http.get<QuestionnaireImpact>(`${this.base}/${code}/versions/${versionNumber}/impact`);
  }

  reviews(code: string, versionNumber: number): Observable<AdminReview[]> {
    return this.http.get<AdminReview[]>(`${this.base}/${code}/versions/${versionNumber}/reviews`);
  }
}
