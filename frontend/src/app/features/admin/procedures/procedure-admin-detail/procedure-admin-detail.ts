import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AdminProcedureService,
  AdminProcedureVersionSummary,
} from '../../../../core/services/admin/admin-procedure.service';

/** Route: /admin/procedures/:code - identity + full version history (brief §18). */
@Component({
  selector: 'app-procedure-admin-detail',
  imports: [RouterLink, FormsModule],
  templateUrl: './procedure-admin-detail.html',
})
export class ProcedureAdminDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly procedureService = inject(AdminProcedureService);

  protected readonly code = this.route.snapshot.paramMap.get('code')!;
  protected readonly versions = signal<AdminProcedureVersionSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly newTitle = signal('');
  protected readonly createError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.procedureService.versionHistory(this.code).subscribe({
      next: (versions) => {
        this.versions.set(versions);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected createDraft(): void {
    this.createError.set(null);
    this.procedureService.createDraftVersion(this.code, { title: this.newTitle() || 'Untitled draft' }).subscribe({
      next: () => {
        this.newTitle.set('');
        this.load();
      },
      error: (err) => this.createError.set(err?.error?.message ?? 'Could not create draft'),
    });
  }
}
