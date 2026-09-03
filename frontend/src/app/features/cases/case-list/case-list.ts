import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CaseService, CaseStatus, CaseSummary } from '../../../core/services/case.service';

const ACTIVE_STATUSES: CaseStatus[] = [
  'DRAFT',
  'PREPARING',
  'READY_TO_SUBMIT',
  'SUBMITTED',
  'WAITING',
  'ADDITIONAL_DOCUMENTS_REQUIRED',
  'DECISION_RECEIVED',
  'APPROVED',
  'REJECTED',
  'APPEAL',
];

/** "My Cases" (brief §40) - active cases first, most recently updated first (already the
 * backend's own ordering); completed/cancelled cases listed separately below. */
@Component({
  selector: 'app-case-list',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './case-list.html',
  styleUrl: './case-list.scss',
})
export class CaseList {
  private readonly caseService = inject(CaseService);

  protected readonly cases = signal<CaseSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly activeCases = computed(() => this.cases().filter((c) => ACTIVE_STATUSES.includes(c.status)));
  protected readonly closedCases = computed(() => this.cases().filter((c) => !ACTIVE_STATUSES.includes(c.status)));

  constructor() {
    this.caseService.list().subscribe({
      next: (cases) => {
        this.cases.set(cases);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected statusLabel(status: CaseStatus): string {
    return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase());
  }
}
