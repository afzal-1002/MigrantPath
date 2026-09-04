import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CaseAuthorityRef, CaseOfficeRef, CaseSummary } from './case.service';
import { Recommendation } from './recommendation.service';

export interface DashboardProfile {
  displayName: string;
  email: string;
  accountVerified: boolean;
  city: string;
}

/** {@code severity} drives the frontend's own color mapping - never a raw value shown
 * to the user. {@code caseId}/{@code assessmentId} are whichever one applies. */
export type NextActionSeverity = 'ATTENTION' | 'NEXT' | 'INFO';

export interface DashboardNextAction {
  heading: string;
  detail: string;
  severity: NextActionSeverity;
  ctaLabel: string;
  caseId: string | null;
  assessmentId: string | null;
}

export interface DashboardCase {
  id: string;
  procedureCode: string;
  procedureTitle: string;
  status: string;
  startedAt: string;
  daysActive: number;
  stepsCompleted: number;
  stepsTotal: number;
  documentsCompleted: number;
  documentsTotal: number;
  feesCompleted: number;
  feesTotal: number;
  hasRequirementUpdates: boolean;
  stageIndex: number;
  stageLabel: string;
  authorities: CaseAuthorityRef[];
  offices: CaseOfficeRef[];
}

export type DashboardDateType = 'CASE_STARTED' | 'CASE_SUBMITTED' | 'CASE_COMPLETED';

export interface DashboardDate {
  label: string;
  date: string;
  type: DashboardDateType;
}

export interface DashboardMilestone {
  label: string;
  occurredAt: string;
}

export interface DashboardActivity {
  label: string;
  occurredAt: string;
}

export interface DashboardAssessmentStatus {
  assessmentId: string;
  status: string;
  progressPercent: number;
}

export interface Dashboard {
  profile: DashboardProfile;
  primaryCase: DashboardCase | null;
  activeCases: CaseSummary[];
  nextActions: DashboardNextAction[];
  importantDates: DashboardDate[];
  completedMilestones: DashboardMilestone[];
  latestRecommendations: Recommendation[];
  recentActivity: DashboardActivity[];
  assessmentStatus: DashboardAssessmentStatus | null;
}

/**
 * Post-MVP UX Milestone UX1 (redesign pass) - a thin HTTP client for the single dashboard
 * aggregation endpoint (brief §27-§34). Never computes progress/dates/next-actions itself; only
 * reflects what {@code DashboardService} on the backend already decided from real data.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/dashboard`;

  get(): Observable<Dashboard> {
    return this.http.get<Dashboard>(this.base);
  }
}
