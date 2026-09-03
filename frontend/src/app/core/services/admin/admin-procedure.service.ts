import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminProcedureSummary {
  code: string;
  categoryCode: string;
  canonicalName: string;
  jurisdictionScope: string;
  active: boolean;
  activeVersionNumber: number | null;
  activeVersionEffectiveFrom: string | null;
  latestVersionNumber: number | null;
  latestVersionStatus: string | null;
}

export interface AdminActor {
  id: string;
  email: string;
}

export interface AdminStep {
  id: string;
  stableCode: string;
  title: string;
  description: string | null;
  detailedInstructions: string | null;
  stepType: string;
  sortOrder: number;
  mandatory: boolean;
}

export interface AdminDocument {
  id: string;
  stableCode: string;
  name: string;
  description: string | null;
  requirementType: string;
  requiredByDefault: boolean;
  numberOfCopies: number | null;
  originalRequired: boolean | null;
  copyRequired: boolean | null;
  translationRequired: boolean | null;
  swornTranslationRequired: boolean | null;
  apostilleRequired: boolean | null;
  legalisationRequired: boolean | null;
  validityPeriodDescription: string | null;
  notes: string | null;
  sortOrder: number;
}

export interface AdminFee {
  id: string;
  stableCode: string;
  amount: number;
  currency: string;
  description: string | null;
  paymentInstructions: string | null;
  refundable: boolean | null;
}

export interface AdminSourceRef {
  officialSourceId: string;
  title: string;
  sourceUrl: string;
  role: string;
  verificationStatus: string;
}

export interface AdminProcedureVersionDetail {
  id: string;
  procedureCode: string;
  versionNumber: number;
  title: string;
  summary: string | null;
  description: string | null;
  status: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  changeSummary: string | null;
  lockVersion: number;
  createdBy: AdminActor | null;
  submittedBy: AdminActor | null;
  approvedBy: AdminActor | null;
  publishedBy: AdminActor | null;
  submittedAt: string | null;
  approvedAt: string | null;
  publishedAt: string | null;
  steps: AdminStep[];
  documents: AdminDocument[];
  fees: AdminFee[];
  sources: AdminSourceRef[];
}

export interface AdminProcedureVersionSummary {
  procedureCode: string;
  versionNumber: number;
  status: string;
  title: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}

export interface ValidationIssue {
  code: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
}

export interface ProcedureVersionDiff {
  fromVersionId: string;
  fromVersionNumber: number;
  toVersionId: string;
  toVersionNumber: number;
  overviewChanges: string[];
  stepsAdded: string[];
  stepsRemoved: string[];
  stepsChanged: string[];
  documentsAdded: string[];
  documentsRemoved: string[];
  documentsChanged: string[];
  feesAdded: string[];
  feesRemoved: string[];
  feesChanged: string[];
}

export interface ProcedureVersionImpact {
  activeUserCases: number;
}

export interface AdminReview {
  id: string;
  entityType: string;
  entityVersionId: string;
  submittedByEmail: string;
  reviewerEmail: string | null;
  status: string;
  comment: string | null;
  createdAt: string;
  completedAt: string | null;
}

/**
 * Admin client for Procedures (brief §94: one service per content type, not one giant
 * AdminService). Mutation endpoints live under `/api/v1/admin/procedures` (Phase 9); creating a
 * procedure identity/draft version/step/document/source still goes through the pre-existing Phase
 * 4 `/api/v1/internal/content` endpoints this service also wraps, so the admin UI never has to
 * know which prefix owns which action.
 */
@Injectable({ providedIn: 'root' })
export class AdminProcedureService {
  private readonly http = inject(HttpClient);
  private readonly adminBase = `${environment.apiBaseUrl}/admin/procedures`;
  private readonly legacyBase = `${environment.apiBaseUrl}/internal/content`;

  list(): Observable<AdminProcedureSummary[]> {
    return this.http.get<AdminProcedureSummary[]>(this.adminBase);
  }

  versionHistory(code: string): Observable<AdminProcedureVersionSummary[]> {
    return this.http.get<AdminProcedureVersionSummary[]>(`${this.adminBase}/${code}`);
  }

  versionDetail(code: string, versionNumber: number): Observable<AdminProcedureVersionDetail> {
    return this.http.get<AdminProcedureVersionDetail>(`${this.adminBase}/${code}/versions/${versionNumber}`);
  }

  createProcedure(request: {
    code: string;
    categoryCode: string;
    canonicalName: string;
    shortDescription?: string;
    jurisdictionScope: string;
  }): Observable<string> {
    return this.http.post(`${this.legacyBase}/procedures`, request, { responseType: 'text' });
  }

  createDraftVersion(
    code: string,
    request: { title: string; summary?: string; description?: string },
  ): Observable<AdminProcedureVersionSummary> {
    return this.http.post<AdminProcedureVersionSummary>(`${this.legacyBase}/procedures/${code}/versions`, request);
  }

  copyVersion(code: string, versionNumber: number): Observable<AdminProcedureVersionDetail> {
    return this.http.post<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/copy`,
      {},
    );
  }

  updateOverview(
    code: string,
    versionNumber: number,
    request: { title: string; summary?: string; description?: string; effectiveFrom?: string; changeSummary?: string },
  ): Observable<AdminProcedureVersionDetail> {
    return this.http.patch<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}`,
      request,
    );
  }

  addStep(code: string, versionNumber: number, request: {
    stableCode: string; title: string; description?: string; stepType: string; sortOrder: number; mandatory: boolean;
  }): Observable<unknown> {
    return this.http.post(`${this.legacyBase}/procedures/${code}/versions/${versionNumber}/steps`, request);
  }

  updateStep(
    code: string,
    versionNumber: number,
    stepId: string,
    request: { title: string; description?: string; stepType: string; sortOrder: number; mandatory: boolean },
  ): Observable<AdminProcedureVersionDetail> {
    return this.http.patch<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/steps/${stepId}`,
      request,
    );
  }

  removeStep(code: string, versionNumber: number, stepId: string): Observable<AdminProcedureVersionDetail> {
    return this.http.delete<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/steps/${stepId}`,
    );
  }

  addDocument(code: string, versionNumber: number, request: {
    stableCode: string; documentTypeCode?: string; name: string; description?: string; requirementType: string;
    requiredByDefault: boolean; sortOrder: number;
  }): Observable<unknown> {
    return this.http.post(`${this.legacyBase}/procedures/${code}/versions/${versionNumber}/documents`, request);
  }

  updateDocument(
    code: string,
    versionNumber: number,
    documentId: string,
    request: Omit<AdminDocument, 'id' | 'stableCode'>,
  ): Observable<AdminProcedureVersionDetail> {
    return this.http.patch<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/documents/${documentId}`,
      request,
    );
  }

  removeDocument(code: string, versionNumber: number, documentId: string): Observable<AdminProcedureVersionDetail> {
    return this.http.delete<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/documents/${documentId}`,
    );
  }

  addFee(
    code: string,
    versionNumber: number,
    request: { stableCode: string; feeType: string; amount: number; currency: string },
  ): Observable<AdminProcedureVersionDetail> {
    return this.http.post<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/fees`,
      request,
    );
  }

  updateFee(
    code: string,
    versionNumber: number,
    feeId: string,
    request: { amount: number; currency: string; description?: string; paymentInstructions?: string; refundable?: boolean },
  ): Observable<AdminProcedureVersionDetail> {
    return this.http.patch<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/fees/${feeId}`,
      request,
    );
  }

  removeFee(code: string, versionNumber: number, feeId: string): Observable<AdminProcedureVersionDetail> {
    return this.http.delete<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/fees/${feeId}`,
    );
  }

  attachSource(
    code: string,
    versionNumber: number,
    request: { officialSourceId: string; role: string },
  ): Observable<unknown> {
    return this.http.post(`${this.legacyBase}/procedures/${code}/versions/${versionNumber}/sources`, request);
  }

  submit(code: string, versionNumber: number): Observable<AdminProcedureVersionDetail> {
    return this.http.post<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/submit`,
      {},
    );
  }

  approve(code: string, versionNumber: number, comment?: string): Observable<AdminProcedureVersionDetail> {
    return this.http.post<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/approve`,
      { comment },
    );
  }

  requestChanges(code: string, versionNumber: number, comment: string): Observable<AdminProcedureVersionDetail> {
    return this.http.post<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/request-changes`,
      { comment },
    );
  }

  publish(code: string, versionNumber: number, effectiveFrom: string): Observable<AdminProcedureVersionDetail> {
    return this.http.post<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/publish`,
      { effectiveFrom },
    );
  }

  archive(code: string, versionNumber: number): Observable<AdminProcedureVersionDetail> {
    return this.http.post<AdminProcedureVersionDetail>(
      `${this.adminBase}/${code}/versions/${versionNumber}/archive`,
      {},
    );
  }

  validate(code: string, versionNumber: number, effectiveFrom?: string): Observable<ValidationResult> {
    let params = new HttpParams();
    if (effectiveFrom) {
      params = params.set('effectiveFrom', effectiveFrom);
    }
    return this.http.get<ValidationResult>(`${this.adminBase}/${code}/versions/${versionNumber}/validate`, { params });
  }

  diff(code: string, from: number, to: number): Observable<ProcedureVersionDiff> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ProcedureVersionDiff>(`${this.adminBase}/${code}/diff`, { params });
  }

  impact(code: string, versionNumber: number): Observable<ProcedureVersionImpact> {
    return this.http.get<ProcedureVersionImpact>(`${this.adminBase}/${code}/versions/${versionNumber}/impact`);
  }

  reviews(code: string, versionNumber: number): Observable<AdminReview[]> {
    return this.http.get<AdminReview[]>(`${this.adminBase}/${code}/versions/${versionNumber}/reviews`);
  }
}
