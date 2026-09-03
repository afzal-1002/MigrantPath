import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminReview, ValidationResult } from './admin-procedure.service';

export interface AdminThresholdSummary {
  code: string;
  canonicalName: string;
  valueType: string;
  unit: string | null;
  active: boolean;
}

export interface AdminThresholdVersion {
  id: string;
  thresholdCode: string;
  status: string;
  value: number | null;
  valueText: string | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  notes: string | null;
  lockVersion: number;
  createdByEmail: string | null;
  submittedByEmail: string | null;
  approvedByEmail: string | null;
  publishedByEmail: string | null;
  publishedAt: string | null;
}

export interface ThresholdImpact {
  referencingRuleCodes: string[];
}

/**
 * Admin client for Thresholds (brief §94). {@link AdminThresholdVersion} has no version number
 * (see PHASE_9_REPORT.md's Deviations) - versions are addressed by their UUID {@code id}, not a
 * `code/versionNumber` pair like Procedure/Rule/Questionnaire.
 */
@Injectable({ providedIn: 'root' })
export class AdminThresholdService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/thresholds`;

  list(): Observable<AdminThresholdSummary[]> {
    return this.http.get<AdminThresholdSummary[]>(this.base);
  }

  versions(code: string): Observable<AdminThresholdVersion[]> {
    return this.http.get<AdminThresholdVersion[]>(`${this.base}/${code}`);
  }

  impact(code: string): Observable<ThresholdImpact> {
    return this.http.get<ThresholdImpact>(`${this.base}/${code}/impact`);
  }

  createThreshold(request: { code: string; canonicalName: string; valueType: string }): Observable<string> {
    return this.http.post(this.base, request, { responseType: 'text' });
  }

  createDraftVersion(
    code: string,
    request: { value?: number; valueText?: string; effectiveFrom?: string; notes?: string },
  ): Observable<AdminThresholdVersion> {
    return this.http.post<AdminThresholdVersion>(`${this.base}/${code}/versions`, request);
  }

  updateDraft(
    code: string,
    versionId: string,
    request: { value?: number; valueText?: string; effectiveFrom?: string; notes?: string },
  ): Observable<AdminThresholdVersion> {
    return this.http.patch<AdminThresholdVersion>(`${this.base}/${code}/versions/${versionId}`, request);
  }

  submit(code: string, versionId: string): Observable<AdminThresholdVersion> {
    return this.http.post<AdminThresholdVersion>(`${this.base}/${code}/versions/${versionId}/submit`, {});
  }

  approve(code: string, versionId: string, comment?: string): Observable<AdminThresholdVersion> {
    return this.http.post<AdminThresholdVersion>(`${this.base}/${code}/versions/${versionId}/approve`, { comment });
  }

  requestChanges(code: string, versionId: string, comment: string): Observable<AdminThresholdVersion> {
    return this.http.post<AdminThresholdVersion>(`${this.base}/${code}/versions/${versionId}/request-changes`, {
      comment,
    });
  }

  publish(code: string, versionId: string, effectiveFrom: string): Observable<AdminThresholdVersion> {
    return this.http.post<AdminThresholdVersion>(`${this.base}/${code}/versions/${versionId}/publish`, {
      effectiveFrom,
    });
  }

  archive(code: string, versionId: string): Observable<AdminThresholdVersion> {
    return this.http.post<AdminThresholdVersion>(`${this.base}/${code}/versions/${versionId}/archive`, {});
  }

  validate(code: string, versionId: string): Observable<ValidationResult> {
    return this.http.get<ValidationResult>(`${this.base}/${code}/versions/${versionId}/validate`);
  }

  reviews(code: string, versionId: string): Observable<AdminReview[]> {
    return this.http.get<AdminReview[]>(`${this.base}/${code}/versions/${versionId}/reviews`);
  }
}
