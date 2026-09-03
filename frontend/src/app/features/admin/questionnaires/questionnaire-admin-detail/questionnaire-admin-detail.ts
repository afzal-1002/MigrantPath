import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AdminQuestionnaireService,
  AdminQuestionnaireVersionDetail,
  QuestionnaireImpact,
} from '../../../../core/services/admin/admin-questionnaire.service';

/**
 * Route: /admin/questionnaires/:code - version list, copy-to-new-draft, publish/archive, and a
 * read-only question listing per version (brief §49/§50) - deep question/dependency editing is a
 * documented scope cut (see PHASE_9_REPORT.md).
 */
@Component({
  selector: 'app-questionnaire-admin-detail',
  imports: [RouterLink, FormsModule],
  templateUrl: './questionnaire-admin-detail.html',
})
export class QuestionnaireAdminDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly questionnaireService = inject(AdminQuestionnaireService);

  protected readonly code = this.route.snapshot.paramMap.get('code')!;
  protected readonly versions = signal<AdminQuestionnaireVersionDetail[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly expanded = signal<Record<number, boolean>>({});
  protected readonly impactByVersion = signal<Record<number, QuestionnaireImpact>>({});

  protected readonly copyFromVersion = signal<number | null>(null);
  protected readonly copyTitle = signal('');
  protected readonly copyDescription = signal('');
  protected readonly publishEffectiveFrom = signal('');
  protected readonly reviewComment = signal('');
  protected readonly actionError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.questionnaireService.versionHistory(this.code).subscribe({
      next: (versions) => {
        this.versions.set(versions);
        this.loading.set(false);
        if (versions.length > 0 && this.copyFromVersion() === null) {
          this.copyFromVersion.set(versions[0].versionNumber);
        }
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected toggleExpand(versionNumber: number): void {
    this.expanded.update((m) => ({ ...m, [versionNumber]: !m[versionNumber] }));
  }

  protected loadImpact(versionNumber: number): void {
    this.questionnaireService.impact(this.code, versionNumber).subscribe((imp) =>
      this.impactByVersion.update((m) => ({ ...m, [versionNumber]: imp })),
    );
  }

  protected copyVersion(): void {
    const from = this.copyFromVersion();
    if (from === null) {
      return;
    }
    this.actionError.set(null);
    this.questionnaireService
      .copyVersion(this.code, from, { title: this.copyTitle() || 'Untitled draft', description: this.copyDescription() })
      .subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected submit(versionNumber: number): void {
    this.questionnaireService.submit(this.code, versionNumber).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected approve(versionNumber: number): void {
    this.questionnaireService.approve(this.code, versionNumber, this.reviewComment()).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected requestChanges(versionNumber: number): void {
    this.questionnaireService.requestChanges(this.code, versionNumber, this.reviewComment()).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected publish(versionNumber: number): void {
    this.questionnaireService.publish(this.code, versionNumber, this.publishEffectiveFrom()).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected archive(versionNumber: number): void {
    this.questionnaireService.archive(this.code, versionNumber).subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }
}
