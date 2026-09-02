import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Typed mirrors of the Phase 4 public procedure DTOs (docs/database/DATABASE.md §3) - "what is
 * this procedure," never an eligibility determination (brief §90). Only ever resolves to a
 * procedure's currently active PUBLISHED version - DRAFT content is never reachable through this
 * service, matching the backend's own guarantee.
 */
export interface ProcedureSummary {
  code: string;
  name: string;
  category: string;
  summary: string | null;
  jurisdictionScope: string;
}

export interface StepInfo {
  code: string;
  title: string;
  description: string | null;
  detailedInstructions: string | null;
  stepType: string;
  sortOrder: number;
  mandatory: boolean;
  onlineAvailable: boolean | null;
  requiresAppointment: boolean | null;
  expectedUserAction: string | null;
}

export interface DocumentRequirementInfo {
  code: string;
  documentType: string | null;
  name: string;
  description: string | null;
  requirementType: 'DEFAULT_REQUIRED' | 'CONDITIONAL' | 'INFORMATIONAL';
  requiredByDefault: boolean;
  numberOfCopies: number | null;
  originalRequired: boolean | null;
  translationRequired: boolean | null;
  swornTranslationRequired: boolean | null;
  apostilleRequired: boolean | null;
  legalisationRequired: boolean | null;
  validityPeriodDescription: string | null;
  notes: string | null;
}

export interface FeeInfo {
  code: string;
  feeType: string;
  /** Serialized as a plain JSON number by Jackson's default BigDecimal handling - never treat
   * this as a string. */
  amount: number;
  currency: string;
  description: string | null;
  paymentInstructions: string | null;
  refundable: boolean | null;
}

export interface AuthorityRef {
  code: string;
  name: string;
  role: string;
  officialWebsite: string | null;
}

export interface OfficeRef {
  code: string;
  name: string;
  street: string | null;
  buildingNumber: string | null;
  postalCode: string | null;
  cityCode: string;
  phone: string | null;
}

export interface SourceInfo {
  authority: string | null;
  title: string;
  url: string;
  role: string;
  lastVerifiedAt: string | null;
}

export interface ProcedureDetail {
  code: string;
  name: string;
  summary: string | null;
  description: string | null;
  category: string;
  jurisdictionScope: string;
  versionNumber: number;
  effectiveFrom: string | null;
  steps: StepInfo[];
  documents: DocumentRequirementInfo[];
  fees: FeeInfo[];
  authorities: AuthorityRef[];
  offices: OfficeRef[];
  sources: SourceInfo[];
  contentReviewedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProcedureService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/procedures`;

  getProcedures(): Observable<ProcedureSummary[]> {
    return this.http.get<ProcedureSummary[]>(this.base);
  }

  getProcedure(code: string): Observable<ProcedureDetail> {
    return this.http.get<ProcedureDetail>(`${this.base}/${encodeURIComponent(code)}`);
  }
}
