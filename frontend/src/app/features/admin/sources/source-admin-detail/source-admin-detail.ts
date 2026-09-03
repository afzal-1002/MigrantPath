import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AdminSource,
  AdminSourceService,
  SourceUsage,
  SourceVerificationRecord,
} from '../../../../core/services/admin/admin-source.service';

/** Route: /admin/sources/:id (brief §29-§34). */
@Component({
  selector: 'app-source-admin-detail',
  imports: [RouterLink, FormsModule],
  templateUrl: './source-admin-detail.html',
})
export class SourceAdminDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly sourceService = inject(AdminSourceService);

  protected readonly id = this.route.snapshot.paramMap.get('id')!;
  protected readonly source = signal<AdminSource | null>(null);
  protected readonly verifications = signal<SourceVerificationRecord[]>([]);
  protected readonly usage = signal<SourceUsage | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly verifyStatus = signal('VERIFIED');
  protected readonly verifyNotes = signal('');
  protected readonly verifyError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.sourceService.detail(this.id).subscribe({
      next: (s) => {
        this.source.set(s);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
    this.sourceService.verifications(this.id).subscribe((v) => this.verifications.set(v));
    this.sourceService.usage(this.id).subscribe((u) => this.usage.set(u));
  }

  protected verify(): void {
    this.verifyError.set(null);
    this.sourceService.verify(this.id, this.verifyStatus(), this.verifyNotes() || undefined).subscribe({
      next: () => {
        this.verifyNotes.set('');
        this.load();
      },
      error: (err) => this.verifyError.set(err?.error?.message ?? 'Could not record verification'),
    });
  }
}
