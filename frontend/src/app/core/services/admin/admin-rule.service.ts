import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminActor, AdminReview, ValidationResult } from './admin-procedure.service';

export type { AdminReview };

export interface AdminRuleSummary {
  code: string;
  canonicalName: string;
  ruleType: string;
  targetType: string;
  targetCode: string;
  active: boolean;
  activeVersionNumber: number | null;
  latestVersionNumber: number | null;
  latestVersionStatus: string | null;
}

export interface AdminRuleSourceRef {
  officialSourceId: string;
  title: string;
  sourceUrl: string;
  role: string;
  verificationStatus: string;
}

export interface AdminRuleVersionDetail {
  id: string;
  ruleCode: string;
  versionNumber: number;
  status: string;
  conditionTree: string;
  explanationKey: string | null;
  changeSummary: string | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  lockVersion: number;
  createdBy: AdminActor | null;
  submittedBy: AdminActor | null;
  approvedBy: AdminActor | null;
  publishedBy: AdminActor | null;
  submittedAt: string | null;
  approvedAt: string | null;
  publishedAt: string | null;
  sources: AdminRuleSourceRef[];
}

export interface RuleVersionDiff {
  fromVersionId: string;
  fromVersionNumber: number;
  toVersionId: string;
  toVersionNumber: number;
  conditionTreeChanged: boolean;
  fromConditionTree: string;
  toConditionTree: string;
  explanationKeyChanged: boolean;
  fromExplanationKey: string | null;
  toExplanationKey: string | null;
}

export interface ConditionTrace {
  code: string | null;
  path: string;
  fact: string | null;
  operator: string | null;
  result: 'PASS' | 'FAIL' | 'MISSING' | 'ERROR';
  explanationKey: string | null;
  description: string;
}

export interface RuleEvaluationResult {
  ruleId: string | null;
  ruleCode: string;
  ruleType: string | null;
  ruleVersionId: string | null;
  ruleVersionNumber: number;
  targetType: string | null;
  targetCode: string | null;
  evaluationDate: string;
  status: 'SATISFIED' | 'NOT_SATISFIED' | 'INDETERMINATE' | 'ERROR';
  passed: ConditionTrace[];
  failed: ConditionTrace[];
  missing: ConditionTrace[];
  errors: ConditionTrace[];
  missingFacts: string[];
  explanationKey: string | null;
}

/** Admin client for Rules (brief §94), mirroring {@link AdminProcedureService}. */
@Injectable({ providedIn: 'root' })
export class AdminRuleService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/rules`;

  list(): Observable<AdminRuleSummary[]> {
    return this.http.get<AdminRuleSummary[]>(this.base);
  }

  versionHistory(code: string): Observable<AdminRuleVersionDetail[]> {
    return this.http.get<AdminRuleVersionDetail[]>(`${this.base}/${code}`);
  }

  createRule(request: {
    code: string;
    canonicalName: string;
    ruleType: string;
    targetType: string;
    targetCode: string;
  }): Observable<string> {
    return this.http.post(this.base, request, { responseType: 'text' });
  }

  createDraftVersion(
    code: string,
    request: { conditionTree: string; explanationKey?: string },
  ): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions`, request);
  }

  copyVersion(code: string, versionNumber: number): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/copy`, {});
  }

  updateDraft(
    code: string,
    versionNumber: number,
    request: { conditionTree: string; explanationKey?: string; changeSummary?: string },
  ): Observable<AdminRuleVersionDetail> {
    return this.http.patch<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}`, request);
  }

  attachSource(
    code: string,
    versionNumber: number,
    request: { officialSourceId: string; role: string },
  ): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/sources`, request);
  }

  submit(code: string, versionNumber: number): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/submit`, {});
  }

  approve(code: string, versionNumber: number, comment?: string): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/approve`, { comment });
  }

  requestChanges(code: string, versionNumber: number, comment: string): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/request-changes`, {
      comment,
    });
  }

  publish(code: string, versionNumber: number, effectiveFrom: string): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/publish`, {
      effectiveFrom,
    });
  }

  archive(code: string, versionNumber: number): Observable<AdminRuleVersionDetail> {
    return this.http.post<AdminRuleVersionDetail>(`${this.base}/${code}/versions/${versionNumber}/archive`, {});
  }

  validate(code: string, versionNumber: number, effectiveFrom?: string): Observable<ValidationResult> {
    let params = new HttpParams();
    if (effectiveFrom) {
      params = params.set('effectiveFrom', effectiveFrom);
    }
    return this.http.get<ValidationResult>(`${this.base}/${code}/versions/${versionNumber}/validate`, { params });
  }

  validateTree(conditionTree: string): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${this.base}/validate-tree`, { conditionTree });
  }

  dryRun(request: {
    conditionTree: string;
    explanationKey?: string;
    facts: Record<string, unknown>;
    evaluationDate?: string;
  }): Observable<RuleEvaluationResult> {
    return this.http.post<RuleEvaluationResult>(`${this.base}/dry-run`, request);
  }

  diff(code: string, from: number, to: number): Observable<RuleVersionDiff> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<RuleVersionDiff>(`${this.base}/${code}/diff`, { params });
  }

  reviews(code: string, versionNumber: number): Observable<AdminReview[]> {
    return this.http.get<AdminReview[]>(`${this.base}/${code}/versions/${versionNumber}/reviews`);
  }
}
