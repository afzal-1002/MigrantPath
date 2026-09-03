import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminSource, AdminSourceService } from '../../../../core/services/admin/admin-source.service';

/** Route: /admin/sources (brief §28). */
@Component({
  selector: 'app-source-admin-list',
  imports: [RouterLink, FormsModule],
  templateUrl: './source-admin-list.html',
})
export class SourceAdminList {
  private readonly sourceService = inject(AdminSourceService);

  protected readonly sources = signal<AdminSource[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly statusFilter = signal('');
  protected readonly showCreate = signal(false);

  protected readonly newTitle = signal('');
  protected readonly newUrl = signal('');
  protected readonly newType = signal('OFFICIAL_SERVICE_PAGE');
  protected readonly createError = signal<string | null>(null);

  protected readonly filtered = computed(() => {
    const status = this.statusFilter();
    return status ? this.sources().filter((s) => s.verificationStatus === status) : this.sources();
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.sourceService.list().subscribe({
      next: (s) => {
        this.sources.set(s);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected create(): void {
    this.createError.set(null);
    this.sourceService.create({ title: this.newTitle(), sourceUrl: this.newUrl(), sourceType: this.newType() }).subscribe({
      next: () => {
        this.showCreate.set(false);
        this.newTitle.set('');
        this.newUrl.set('');
        this.load();
      },
      error: (err) => this.createError.set(err?.error?.message ?? 'Could not create source'),
    });
  }
}
