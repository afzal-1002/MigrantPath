import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminProcedureSummary, AdminProcedureService } from '../../../../core/services/admin/admin-procedure.service';

/** Route: /admin/procedures (brief §17). */
@Component({
  selector: 'app-procedure-admin-list',
  imports: [RouterLink, FormsModule],
  templateUrl: './procedure-admin-list.html',
})
export class ProcedureAdminList {
  private readonly procedureService = inject(AdminProcedureService);

  protected readonly procedures = signal<AdminProcedureSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly search = signal('');
  protected readonly showCreate = signal(false);

  protected readonly newCode = signal('');
  protected readonly newCategory = signal('OTHER');
  protected readonly newName = signal('');
  protected readonly newJurisdiction = signal('NATIONAL');
  protected readonly createError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.procedureService.list().subscribe({
      next: (procedures) => {
        this.procedures.set(procedures);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected filtered(): AdminProcedureSummary[] {
    const q = this.search().trim().toLowerCase();
    if (!q) {
      return this.procedures();
    }
    return this.procedures().filter(
      (p) => p.code.toLowerCase().includes(q) || p.canonicalName.toLowerCase().includes(q),
    );
  }

  protected createProcedure(): void {
    this.createError.set(null);
    this.procedureService
      .createProcedure({
        code: this.newCode(),
        categoryCode: this.newCategory(),
        canonicalName: this.newName(),
        jurisdictionScope: this.newJurisdiction(),
      })
      .subscribe({
        next: () => {
          this.showCreate.set(false);
          this.newCode.set('');
          this.newName.set('');
          this.load();
        },
        error: (err) => this.createError.set(err?.error?.message ?? 'Could not create procedure'),
      });
  }
}
