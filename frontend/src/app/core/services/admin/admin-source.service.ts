import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminSource {
  id: string;
  title: string;
  sourceUrl: string;
  sourceType: string;
  language: string | null;
  publicationDate: string | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  verificationStatus: string;
  lastCheckedAt: string | null;
  lastVerifiedAt: string | null;
  active: boolean;
}

export interface SourceVerificationRecord {
  id: string;
  checkedAt: string;
  checkedByEmail: string | null;
  status: string;
  notes: string | null;
}

export interface SourceUsage {
  procedureVersions: number;
  ruleVersions: number;
  thresholdVersions: number;
}

/**
 * Admin client for Official Sources (brief §94). {@code verify(..., 'OUTDATED', reason)} is the
 * "mark outdated" workflow (brief §34) - reusing the one verification action rather than a
 * separate endpoint, per {@code AdminSourceController}'s own design.
 */
@Injectable({ providedIn: 'root' })
export class AdminSourceService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/sources`;

  list(): Observable<AdminSource[]> {
    return this.http.get<AdminSource[]>(this.base);
  }

  detail(id: string): Observable<AdminSource> {
    return this.http.get<AdminSource>(`${this.base}/${id}`);
  }

  create(request: { title: string; sourceUrl: string; sourceType: string }): Observable<AdminSource> {
    return this.http.post<AdminSource>(this.base, request);
  }

  verify(id: string, status: string, notes?: string): Observable<AdminSource> {
    return this.http.post<AdminSource>(`${this.base}/${id}/verify`, { status, notes });
  }

  verifications(id: string): Observable<SourceVerificationRecord[]> {
    return this.http.get<SourceVerificationRecord[]>(`${this.base}/${id}/verifications`);
  }

  usage(id: string): Observable<SourceUsage> {
    return this.http.get<SourceUsage>(`${this.base}/${id}/usage`);
  }
}
