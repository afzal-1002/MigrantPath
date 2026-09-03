import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminDashboardService, AdminDashboardSummary } from '../../../core/services/admin/admin-dashboard.service';

/** Operational summary only (brief §16) - counts to act on, never vanity statistics. */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink],
  templateUrl: './admin-dashboard.html',
})
export class AdminDashboard {
  private readonly dashboardService = inject(AdminDashboardService);

  protected readonly summary = signal<AdminDashboardSummary | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.dashboardService.summary().subscribe({
      next: (s) => {
        this.summary.set(s);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
