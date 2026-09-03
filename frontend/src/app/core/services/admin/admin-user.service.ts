import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface AdminUser {
  id: string;
  email: string;
  status: string;
  emailVerified: boolean;
  createdAt: string;
  roles: string[];
}

/**
 * Admin client for role management (brief §81-§83/§94) - ADMIN-only. Deliberately exposes nothing
 * beyond email/status/roles - never Assessments, salary, citizenship, family details, or
 * UserCases (brief §83/§133).
 */
@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/users`;

  search(email: string): Observable<AdminUser[]> {
    const params = new HttpParams().set('email', email);
    return this.http.get<AdminUser[]>(this.base, { params });
  }

  assignRole(userId: string, roleCode: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/${userId}/roles`, { roleCode });
  }

  removeRole(userId: string, roleCode: string): Observable<AdminUser> {
    return this.http.delete<AdminUser>(`${this.base}/${userId}/roles/${roleCode}`);
  }
}
