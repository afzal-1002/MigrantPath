import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminThresholdService, AdminThresholdSummary } from '../../../../core/services/admin/admin-threshold.service';

/** Route: /admin/thresholds (brief §46). */
@Component({
  selector: 'app-threshold-admin-list',
  imports: [RouterLink, FormsModule],
  templateUrl: './threshold-admin-list.html',
})
export class ThresholdAdminList {
  private readonly thresholdService = inject(AdminThresholdService);

  protected readonly thresholds = signal<AdminThresholdSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly showCreate = signal(false);

  protected readonly newCode = signal('');
  protected readonly newName = signal('');
  protected readonly newValueType = signal('MONEY');
  protected readonly createError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.thresholdService.list().subscribe({
      next: (t) => {
        this.thresholds.set(t);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected createThreshold(): void {
    this.createError.set(null);
    this.thresholdService
      .createThreshold({ code: this.newCode(), canonicalName: this.newName(), valueType: this.newValueType() })
      .subscribe({
        next: () => {
          this.showCreate.set(false);
          this.load();
        },
        error: (err) => this.createError.set(err?.error?.message ?? 'Could not create threshold'),
      });
  }
}
