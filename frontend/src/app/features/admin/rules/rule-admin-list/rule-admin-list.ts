import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminRuleService, AdminRuleSummary } from '../../../../core/services/admin/admin-rule.service';

/** Route: /admin/rules (brief §36). */
@Component({
  selector: 'app-rule-admin-list',
  imports: [RouterLink, FormsModule],
  templateUrl: './rule-admin-list.html',
})
export class RuleAdminList {
  private readonly ruleService = inject(AdminRuleService);

  protected readonly rules = signal<AdminRuleSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly showCreate = signal(false);

  protected readonly newCode = signal('');
  protected readonly newName = signal('');
  protected readonly newRuleType = signal('ELIGIBILITY');
  protected readonly newTargetType = signal('PROCEDURE');
  protected readonly newTargetCode = signal('');
  protected readonly createError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    this.ruleService.list().subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected createRule(): void {
    this.createError.set(null);
    this.ruleService
      .createRule({
        code: this.newCode(),
        canonicalName: this.newName(),
        ruleType: this.newRuleType(),
        targetType: this.newTargetType(),
        targetCode: this.newTargetCode(),
      })
      .subscribe({
        next: () => {
          this.showCreate.set(false);
          this.newCode.set('');
          this.load();
        },
        error: (err) => this.createError.set(err?.error?.message ?? 'Could not create rule'),
      });
  }
}
