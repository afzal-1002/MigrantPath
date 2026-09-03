import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type CaseStatus =
  | 'DRAFT'
  | 'PREPARING'
  | 'READY_TO_SUBMIT'
  | 'SUBMITTED'
  | 'WAITING'
  | 'ADDITIONAL_DOCUMENTS_REQUIRED'
  | 'DECISION_RECEIVED'
  | 'APPROVED'
  | 'REJECTED'
  | 'APPEAL'
  | 'COMPLETED'
  | 'CANCELLED';

export type CaseStepStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'BLOCKED' | 'NOT_APPLICABLE';

export type CaseDocumentStatus = 'NOT_STARTED' | 'MISSING' | 'IN_PROGRESS' | 'READY' | 'NEEDS_UPDATE' | 'NOT_APPLICABLE';

export type CaseDocumentApplicability = 'APPLICABLE' | 'NEEDS_CONFIRMATION' | 'NOT_APPLICABLE';

export type CaseFeeStatus = 'NOT_PAID' | 'PAID' | 'NOT_APPLICABLE' | 'UNKNOWN';

export interface CaseSummary {
  id: string;
  procedureCode: string;
  procedureTitle: string;
  status: CaseStatus;
  stepsCompleted: number;
  stepsTotal: number;
  documentsReady: number;
  documentsTotal: number;
  hasRequirementUpdates: boolean;
  updatedAt: string;
}

export interface CaseStep {
  id: string;
  stableCode: string;
  title: string;
  description: string | null;
  detailedInstructions: string | null;
  stepType: string;
  sortOrder: number;
  mandatory: boolean;
  status: CaseStepStatus;
  completedAt: string | null;
}

export interface CaseDocument {
  id: string;
  stableCode: string;
  name: string;
  description: string | null;
  requirementType: string;
  applicability: CaseDocumentApplicability;
  mandatory: boolean;
  numberOfCopies: number | null;
  originalRequired: boolean | null;
  translationRequired: boolean | null;
  swornTranslationRequired: boolean | null;
  apostilleRequired: boolean | null;
  legalisationRequired: boolean | null;
  validityPeriodDescription: string | null;
  contentNotes: string | null;
  userNote: string | null;
  sortOrder: number;
  status: CaseDocumentStatus;
  readyAt: string | null;
}

export interface CaseFee {
  id: string;
  stableCode: string;
  feeType: string;
  amount: number;
  currency: string;
  description: string | null;
  paymentInstructions: string | null;
  sortOrder: number;
  status: CaseFeeStatus;
  paidAt: string | null;
}

export interface CaseAuthorityRef {
  code: string;
  name: string;
  role: string;
  officialWebsite: string | null;
}

export interface CaseOfficeRef {
  code: string;
  name: string;
  street: string | null;
  buildingNumber: string | null;
  postalCode: string | null;
  cityCode: string;
  phone: string | null;
}

export interface CaseSourceRef {
  authority: string | null;
  title: string;
  url: string;
  role: string;
  lastVerifiedAt: string | null;
}

export interface CaseProgress {
  stepsCompleted: number;
  stepsTotal: number;
  documentsReady: number;
  documentsTotal: number;
  conditionalDocumentsToReview: number;
}

export interface CaseDetail {
  id: string;
  procedureCode: string;
  procedureTitle: string;
  status: CaseStatus;
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
  completedAt: string | null;
  evaluationDate: string | null;
  revisionNumber: number;
  progress: CaseProgress;
  steps: CaseStep[];
  documents: CaseDocument[];
  fees: CaseFee[];
  authorities: CaseAuthorityRef[];
  offices: CaseOfficeRef[];
  sources: CaseSourceRef[];
  hasRequirementUpdates: boolean;
}

export interface RequirementChange {
  changeType: 'ADDED' | 'CHANGED' | 'REMOVED';
  category: 'STEP' | 'DOCUMENT' | 'FEE';
  stableCode: string;
  title: string;
  detail: string;
}

export interface RequirementChangeReport {
  newerVersionAvailable: boolean;
  changes: RequirementChange[];
}

export interface CaseEvent {
  eventType: string;
  occurredAt: string;
  metadata: string | null;
}

/**
 * Thin HTTP client for Phase 8's user cases - never decides a checklist item's applicability or
 * status itself (brief §70), only reflects whatever the backend snapshot/engine already decided.
 */
@Injectable({ providedIn: 'root' })
export class CaseService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/cases`;

  /** Starts tracking a recommended pathway (brief §4/§52) - idempotent per recommendation. */
  create(recommendationId: string): Observable<CaseDetail> {
    return this.http.post<CaseDetail>(`${environment.apiBaseUrl}/recommendations/${recommendationId}/cases`, {});
  }

  list(): Observable<CaseSummary[]> {
    return this.http.get<CaseSummary[]>(this.base);
  }

  get(caseId: string): Observable<CaseDetail> {
    return this.http.get<CaseDetail>(`${this.base}/${caseId}`);
  }

  updateStatus(caseId: string, status: CaseStatus): Observable<CaseDetail> {
    return this.http.patch<CaseDetail>(`${this.base}/${caseId}/status`, { status });
  }

  updateStep(caseId: string, stepId: string, status: CaseStepStatus): Observable<CaseDetail> {
    return this.http.patch<CaseDetail>(`${this.base}/${caseId}/steps/${stepId}`, { status });
  }

  updateDocument(
    caseId: string,
    documentId: string,
    update: { status?: CaseDocumentStatus; userNote?: string },
  ): Observable<CaseDetail> {
    return this.http.patch<CaseDetail>(`${this.base}/${caseId}/documents/${documentId}`, update);
  }

  updateFee(caseId: string, feeId: string, status: CaseFeeStatus): Observable<CaseDetail> {
    return this.http.patch<CaseDetail>(`${this.base}/${caseId}/fees/${feeId}`, { status });
  }

  getRequirementChanges(caseId: string): Observable<RequirementChangeReport> {
    return this.http.get<RequirementChangeReport>(`${this.base}/${caseId}/requirement-changes`);
  }

  /** Explicit only (brief §31) - never called automatically. */
  upgrade(caseId: string): Observable<CaseDetail> {
    return this.http.post<CaseDetail>(`${this.base}/${caseId}/upgrade`, {});
  }

  getEvents(caseId: string): Observable<CaseEvent[]> {
    return this.http.get<CaseEvent[]>(`${this.base}/${caseId}/events`);
  }
}
