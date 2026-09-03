import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminDashboardSummary {
  draftProcedureVersions: number;
  draftRuleVersions: number;
  draftThresholdVersions: number;
  draftQuestionnaireVersions: number;
  pendingReviews: number;
  approvedProcedureVersionsAwaitingPublication: number;
  approvedRuleVersionsAwaitingPublication: number;
  approvedThresholdVersionsAwaitingPublication: number;
  approvedQuestionnaireVersionsAwaitingPublication: number;
  sourcesNeedingReview: number;
  outdatedSources: number;
}

/** Admin operational-summary client (brief §16/§94). */
@Injectable({ providedIn: 'root' })
export class AdminDashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/dashboard`;

  summary(): Observable<AdminDashboardSummary> {
    return this.http.get<AdminDashboardSummary>(this.base);
  }
}
