import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdminRuleService, AdminRuleVersionDetail } from '../../../../core/services/admin/admin-rule.service';

/** Route: /admin/rules/:code - version history for one rule. */
@Component({
  selector: 'app-rule-admin-detail',
  imports: [RouterLink, FormsModule],
  templateUrl: './rule-admin-detail.html',
})
export class RuleAdminDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly ruleService = inject(AdminRuleService);

  protected readonly code = this.route.snapshot.paramMap.get('code')!;
  protected readonly versions = signal<AdminRuleVersionDetail[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly newConditionTree = signal('{\n  "fact": "",\n  "operator": "EQUALS",\n  "value": ""\n}');
  protected readonly newExplanationKey = signal('');
  protected readonly createError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.ruleService.versionHistory(this.code).subscribe({
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
    this.ruleService
      .createDraftVersion(this.code, { conditionTree: this.newConditionTree(), explanationKey: this.newExplanationKey() || undefined })
      .subscribe({
        next: () => this.load(),
        error: (err) => this.createError.set(err?.error?.message ?? 'Could not create draft'),
      });
  }
}
