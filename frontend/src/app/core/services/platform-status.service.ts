import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * The one thing this endpoint is for (brief §14): proving the Angular app can reach
 * the Spring Boot backend, and showing which backend version it's talking to. Not a
 * general-purpose API client - real feature services arrive with their own modules
 * starting Phase 2 (see docs/architecture/ARCHITECTURE.md §10).
 */
export interface PlatformStatus {
  status: string;
  application: string;
  version: string;
}

@Injectable({ providedIn: 'root' })
export class PlatformStatusService {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<PlatformStatus> {
    return this.http.get<PlatformStatus>(`${environment.apiBaseUrl}/platform/status`);
  }
}
