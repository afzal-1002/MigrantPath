import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  CaseDetail,
  CaseDocument,
  CaseDocumentStatus,
  CaseFee,
  CaseFeeStatus,
  CaseService,
  CaseStep,
  CaseStepStatus,
  RequirementChangeReport,
} from '../../../core/services/case.service';

interface ApiErrorBody {
  code?: string;
  message?: string;
}

/**
 * Case detail (brief §41/§58): overview, step/document/fee checklists, sources/authorities/
 * offices, and (inline, folded into this one page rather than a separate {@code /cases/:id/
 * updates} route - see PHASE_8_REPORT.md "Deviations") a requirement-updates review with an
 * explicit "Update to latest requirements" action that only ever runs on the user's own click
 * (brief §31/§51 - never automatic).
 */
@Component({
  selector: 'app-case-detail',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './case-detail.html',
  styleUrl: './case-detail.scss',
})
export class CaseDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly caseService = inject(CaseService);

  protected readonly caseId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);
  protected readonly detail = signal<CaseDetail | null>(null);
  protected readonly changes = signal<RequirementChangeReport | null>(null);
  protected readonly showChanges = signal(false);
  protected readonly upgrading = signal(false);
  protected readonly actionError = signal<string | null>(null);

  constructor() {
    if (!this.caseId) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.load();
  }

  private load(): void {
    this.caseService.get(this.caseId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.loading.set(false);
        if (detail.hasRequirementUpdates) {
          this.caseService.getRequirementChanges(this.caseId).subscribe((report) => this.changes.set(report));
        }
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  protected toggleChanges(): void {
    this.showChanges.set(!this.showChanges());
  }

  protected upgrade(): void {
    this.upgrading.set(true);
    this.actionError.set(null);
    this.caseService.upgrade(this.caseId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.changes.set(null);
        this.showChanges.set(false);
        this.upgrading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.upgrading.set(false);
        this.actionError.set(this.errorMessage(err));
      },
    });
  }

  protected setStepStatus(step: CaseStep, status: CaseStepStatus): void {
    this.actionError.set(null);
    this.caseService.updateStep(this.caseId, step.id, status).subscribe({
      next: (detail) => this.detail.set(detail),
      error: (err: HttpErrorResponse) => this.actionError.set(this.errorMessage(err)),
    });
  }

  protected setDocumentStatus(document: CaseDocument, status: CaseDocumentStatus): void {
    this.actionError.set(null);
    this.caseService.updateDocument(this.caseId, document.id, { status }).subscribe({
      next: (detail) => this.detail.set(detail),
      error: (err: HttpErrorResponse) => this.actionError.set(this.errorMessage(err)),
    });
  }

  protected setFeeStatus(fee: CaseFee, status: CaseFeeStatus): void {
    this.actionError.set(null);
    this.caseService.updateFee(this.caseId, fee.id, status).subscribe({
      next: (detail) => this.detail.set(detail),
      error: (err: HttpErrorResponse) => this.actionError.set(this.errorMessage(err)),
    });
  }

  private errorMessage(err: HttpErrorResponse): string {
    const body = err.error as ApiErrorBody | undefined;
    return body?.message ?? 'Something went wrong. Please try again.';
  }

  protected statusLabel(status: string): string {
    return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase());
  }
}
