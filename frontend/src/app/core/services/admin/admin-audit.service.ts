import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AuditLogEntry {
  id: string;
  actorEmail: string | null;
  actionType: string;
  entityType: string;
  entityId: string | null;
  entityBusinessCode: string | null;
  entityVersionId: string | null;
  occurredAt: string;
  summary: string;
}

export interface AuditPage {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface AuditSearchFilters {
  actorId?: string;
  actionType?: string;
  entityType?: string;
  entityBusinessCode?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** Admin client for the audit trail (brief §64/§94) - ADMIN-only, paginated, filtered. */
@Injectable({ providedIn: 'root' })
export class AdminAuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/audit`;

  search(filters: AuditSearchFilters): Observable<AuditPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<AuditPage>(this.base, { params });
  }
}
