import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface FactDefinition {
  code: string;
  valueType: string;
  derived: boolean;
  allowedOperators: string[];
}

/** Backs the Rule condition builder's fact/operator dropdowns (brief §38). */
@Injectable({ providedIn: 'root' })
export class AdminFactService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/facts`;

  list(): Observable<FactDefinition[]> {
    return this.http.get<FactDefinition[]>(this.base);
  }
}
