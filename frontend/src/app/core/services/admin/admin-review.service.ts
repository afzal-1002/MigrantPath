import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AdminReview } from './admin-procedure.service';

export type { AdminReview };

/** Cross-entity review queue (brief §65/§94). */
@Injectable({ providedIn: 'root' })
export class AdminReviewService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/reviews`;

  pendingQueue(): Observable<AdminReview[]> {
    return this.http.get<AdminReview[]>(this.base);
  }
}
