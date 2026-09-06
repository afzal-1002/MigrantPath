import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  CaseDetail,
  CaseDocument,
  CaseDocumentStatus,
  CaseEvent,
  CaseFee,
  CaseFeeStatus,
  CaseService,
  CaseStatus,
  CaseStep,
  CaseStepStatus,
  RequirementChangeReport,
} from '../../../core/services/case.service';
import { formatStatusLabel } from '../../../shared/status-label.util';
import { Icon } from '../../../shared/icon/icon';

interface ApiErrorBody {
  code?: string;
  message?: string;
}

/** The same shared `.ui-badge` modifier vocabulary (styles.scss) already used by
 * case-list and the dashboard - a plain, honest re-bucketing of this app's own real
 * statuses, never a new status this page invents on its own. Reusing it here (instead
 * of a fourth, page-local pill palette) is the actual fix for "not user friendly" -
 * every page in this app now shares one visual language instead of switching styles
 * page to page. */
type PillState = 'success' | 'warning' | 'info';

/** The case's own status badge, reusing the same real CaseStatus values and the same
 * shared `.ui-badge` vocabulary - not a new palette per page. */
const STATUS_BADGE: Record<CaseStatus, string> = {
  DRAFT: '',
  PREPARING: '',
  READY_TO_SUBMIT: 'info',
  SUBMITTED: 'info',
  WAITING: 'info',
  ADDITIONAL_DOCUMENTS_REQUIRED: 'warning',
  DECISION_RECEIVED: 'success',
  APPROVED: 'success',
  APPEAL: 'warning',
  REJECTED: 'danger',
  COMPLETED: 'success',
  CANCELLED: 'danger',
};

/**
 * Case detail (brief §41/§58): overview, step/document/fee checklists, sources/authorities/
 * offices, and (inline, folded into this one page rather than a separate {@code /cases/:id/
 * updates} route - see PHASE_8_REPORT.md "Deviations") a requirement-updates review with an
 * explicit "Update to latest requirements" action that only ever runs on the user's own click
 * (brief §31/§51 - never automatic).
 *
 * UI redesign: one continuous page (no tabs) - Timeline, Documents, Fees, Recent updates
 * all in reading order. Restyled a second time after real user feedback that the
 * previous "archive/paper" treatment (serif type, warm cream palette, a rotated brass
 * stamp) felt out of place next to every other page's own light/white/blue look
 * (Dashboard, Account, the shell itself) - this page now shares that same visual
 * language (the global `.ui-card`/`.ui-badge`/`.ui-avatar` utility classes and
 * `--app-*` tokens) instead of its own one-off palette, which is the actual fix for
 * "not user friendly": one consistent design system across the app, not a fourth
 * distinct aesthetic on this one page. The right rail's real-data substitutions from
 * the earlier pass are unchanged: responsible-authority info (no case-manager/attorney
 * concept exists) and real next pending checklist steps, never an invented date.
 */
@Component({
  selector: 'app-case-detail',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatProgressSpinnerModule, Icon, DatePipe],
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
  protected readonly events = signal<CaseEvent[]>([]);
  protected readonly changes = signal<RequirementChangeReport | null>(null);
  protected readonly showChanges = signal(false);
  protected readonly upgrading = signal(false);
  protected readonly actionError = signal<string | null>(null);

  /** Checklist-completion only (steps + documents), matching the dashboard's own
   * definition exactly - never a legal-probability or eligibility percentage. */
  protected readonly progressPercent = computed(() => {
    const d = this.detail();
    if (!d) {
      return 0;
    }
    const total = d.progress.stepsTotal + d.progress.documentsTotal;
    if (total === 0) {
      return 0;
    }
    return Math.round(((d.progress.stepsCompleted + d.progress.documentsReady) / total) * 100);
  });

  protected readonly daysActive = computed(() => {
    const d = this.detail();
    if (!d) {
      return 0;
    }
    return Math.max(0, Math.floor((Date.now() - new Date(d.createdAt).getTime()) / 86_400_000));
  });

  /** "Step N of Total" (visual style borrowed from the reference's "Petition Progress"
   * card) - N is however many steps are already completed, plus one for the step still
   * ahead, capped at the total so a fully-completed case reads "Step Total of Total"
   * rather than overshooting. */
  protected readonly currentStepNumber = computed(() => {
    const d = this.detail();
    if (!d || d.progress.stepsTotal === 0) {
      return 0;
    }
    return Math.min(d.progress.stepsCompleted + 1, d.progress.stepsTotal);
  });

  protected stepStatusText(step: CaseStep): string {
    if (step.status === 'COMPLETED') {
      return step.completedAt ? new DatePipe('en-US').transform(step.completedAt, 'MMM d') ?? 'Completed' : 'Completed';
    }
    if (step.status === 'IN_PROGRESS') {
      return 'In progress';
    }
    return 'Pending';
  }

  /** Right rail "Next up" (visual style only - real titles, never a fabricated date;
   * see this class's own doc comment on why this replaces the reference's "Your
   * attorney"/"Upcoming" cards). */
  protected readonly nextPendingSteps = computed(() => {
    const d = this.detail();
    if (!d) {
      return [];
    }
    return d.steps.filter((s) => s.status !== 'COMPLETED').slice(0, 4);
  });

  protected documentPillState(status: CaseDocumentStatus): PillState {
    if (status === 'READY' || status === 'NOT_APPLICABLE') {
      return 'success';
    }
    if (status === 'IN_PROGRESS') {
      return 'info';
    }
    return 'warning';
  }

  protected feePillState(status: CaseFeeStatus): PillState {
    if (status === 'PAID' || status === 'NOT_APPLICABLE') {
      return 'success';
    }
    if (status === 'UNKNOWN') {
      return 'info';
    }
    return 'warning';
  }

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
    this.caseService.getEvents(this.caseId).subscribe({
      next: (events) => this.events.set(events),
      error: () => this.events.set([]),
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
    return formatStatusLabel(status);
  }

  protected statusBadgeClass(status: string): string {
    return STATUS_BADGE[status as CaseStatus] ?? '';
  }
}
