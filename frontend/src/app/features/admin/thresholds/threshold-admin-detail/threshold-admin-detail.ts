import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AdminThresholdService,
  AdminThresholdVersion,
  ThresholdImpact,
} from '../../../../core/services/admin/admin-threshold.service';
import { AdminReview, ValidationResult } from '../../../../core/services/admin/admin-procedure.service';

/**
 * Route: /admin/thresholds/:code - version list + inline editing, all in one page (Threshold
 * versions carry far less content than Procedure/Rule, so a separate editor route was judged
 * unnecessary - see PHASE_9_REPORT.md).
 */
@Component({
  selector: 'app-threshold-admin-detail',
  imports: [RouterLink, FormsModule],
  templateUrl: './threshold-admin-detail.html',
})
export class ThresholdAdminDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly thresholdService = inject(AdminThresholdService);

  protected readonly code = this.route.snapshot.paramMap.get('code')!;
  protected readonly versions = signal<AdminThresholdVersion[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly impact = signal<ThresholdImpact | null>(null);

  protected readonly newValue = signal<number | null>(null);
  protected readonly newEffectiveFrom = signal('');
  protected readonly newNotes = signal('');
  protected readonly actionError = signal<string | null>(null);

  protected readonly reviewComment = signal('');
  protected readonly reviewsByVersion = signal<Record<string, AdminReview[]>>({});
  protected readonly validationByVersion = signal<Record<string, ValidationResult>>({});

  constructor() {
    this.load();
    this.thresholdService.impact(this.code).subscribe((i) => this.impact.set(i));
  }

  private load(): void {
    this.thresholdService.versions(this.code).subscribe({
      next: (v) => {
        this.versions.set(v);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected createDraft(): void {
    this.actionError.set(null);
    this.thresholdService
      .createDraftVersion(this.code, {
        value: this.newValue() ?? undefined,
        effectiveFrom: this.newEffectiveFrom() || undefined,
        notes: this.newNotes() || undefined,
      })
      .subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected validate(versionId: string): void {
    this.thresholdService.validate(this.code, versionId).subscribe((v) =>
      this.validationByVersion.update((m) => ({ ...m, [versionId]: v })),
    );
  }

  protected loadReviews(versionId: string): void {
    this.thresholdService.reviews(this.code, versionId).subscribe((r) =>
      this.reviewsByVersion.update((m) => ({ ...m, [versionId]: r })),
    );
  }

  protected submit(versionId: string): void {
    this.thresholdService.submit(this.code, versionId).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected approve(versionId: string): void {
    this.thresholdService.approve(this.code, versionId, this.reviewComment()).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected requestChanges(versionId: string): void {
    this.thresholdService.requestChanges(this.code, versionId, this.reviewComment()).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected publish(versionId: string, effectiveFrom: string): void {
    this.thresholdService.publish(this.code, versionId, effectiveFrom).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected archive(versionId: string): void {
    this.thresholdService.archive(this.code, versionId).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }
}
