import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminAuditService, AuditLogEntry } from '../../../../core/services/admin/admin-audit.service';

/** Route: /admin/audit - ADMIN-only (brief §64). */
@Component({
  selector: 'app-audit-log',
  imports: [FormsModule],
  templateUrl: './audit-log.html',
})
export class AuditLog {
  private readonly auditService = inject(AdminAuditService);

  protected readonly entries = signal<AuditLogEntry[]>([]);
  protected readonly totalPages = signal(0);
  protected readonly page = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly entityBusinessCode = signal('');
  protected readonly entityType = signal('');
  protected readonly actionType = signal('');

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.auditService
      .search({
        entityBusinessCode: this.entityBusinessCode() || undefined,
        entityType: this.entityType() || undefined,
        actionType: this.actionType() || undefined,
        page: this.page(),
        size: 50,
      })
      .subscribe({
        next: (result) => {
          this.entries.set(result.content);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }

  protected search(): void {
    this.page.set(0);
    this.load();
  }

  protected nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update((p) => p + 1);
      this.load();
    }
  }

  protected prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.load();
    }
  }
}
