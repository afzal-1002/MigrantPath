import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) self-service privacy actions (brief §26/§27/§28) -
 * thin wrappers over the backend's own `/api/v1/account/*` endpoints, which operate only on the
 * authenticated caller's own account (never a userId parameter - brief §74).
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = `${environment.apiBaseUrl}/account`;

  /** Downloads the full personal-data export as a Blob - the caller triggers the actual
   * browser save (see Privacy component) since this service has no DOM/document dependency. */
  exportData(): Observable<Blob> {
    return this.http.get(`${this.apiBase}/export`, { responseType: 'blob' });
  }

  deleteAccount(currentPassword: string): Observable<void> {
    return this.http.post<void>(`${this.apiBase}/delete`, {
      currentPassword,
      confirmation: 'DELETE',
    });
  }
}
