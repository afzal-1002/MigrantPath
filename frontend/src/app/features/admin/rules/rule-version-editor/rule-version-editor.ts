import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdminFactService, FactDefinition } from '../../../../core/services/admin/admin-fact.service';
import {
  AdminReview,
  AdminRuleService,
  AdminRuleVersionDetail,
  RuleEvaluationResult,
} from '../../../../core/services/admin/admin-rule.service';
import { ValidationResult } from '../../../../core/services/admin/admin-procedure.service';

interface LeafRow {
  fact: string;
  operator: string;
  valueMode: 'literal' | 'threshold';
  value: string;
  threshold: string;
}

type Section = 'condition' | 'sources' | 'history';

/**
 * Route: /admin/rules/:code/versions/:versionNumber (brief §37-§43). The structured builder
 * supports one ALL/ANY group of leaf conditions - the common case; anything more elaborate (NOT,
 * nested groups) falls back to the "Advanced JSON" editor automatically, a deliberate scope
 * simplification (see PHASE_9_REPORT.md).
 */
@Component({
  selector: 'app-rule-version-editor',
  imports: [RouterLink, FormsModule],
  templateUrl: './rule-version-editor.html',
})
export class RuleVersionEditor {
  private readonly route = inject(ActivatedRoute);
  private readonly ruleService = inject(AdminRuleService);
  private readonly factService = inject(AdminFactService);

  protected readonly code = this.route.snapshot.paramMap.get('code')!;
  protected readonly versionNumber = Number(this.route.snapshot.paramMap.get('versionNumber'));

  protected readonly version = signal<AdminRuleVersionDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly section = signal<Section>('condition');

  protected readonly facts = signal<FactDefinition[]>([]);
  protected readonly builderMode = signal<'simple' | 'advanced'>('simple');
  protected readonly combinator = signal<'ALL' | 'ANY'>('ALL');
  protected readonly leaves = signal<LeafRow[]>([]);
  protected readonly explanationKey = signal('');
  protected readonly changeSummary = signal('');
  protected readonly advancedJson = signal('');

  protected readonly validation = signal<ValidationResult | null>(null);
  protected readonly dryRunFactsJson = signal('{}');
  protected readonly dryRunResult = signal<RuleEvaluationResult | null>(null);
  protected readonly dryRunError = signal<string | null>(null);

  protected readonly attachSourceId = signal('');
  protected readonly attachSourceRole = signal('PRIMARY');
  protected readonly publishEffectiveFrom = signal('');
  protected readonly reviewComment = signal('');
  protected readonly reviews = signal<AdminReview[]>([]);

  constructor() {
    this.factService.list().subscribe((f) => this.facts.set(f));
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.ruleService.versionHistory(this.code).subscribe({
      next: (versions) => {
        const v = versions.find((x) => x.versionNumber === this.versionNumber);
        if (!v) {
          this.error.set('Version not found');
          this.loading.set(false);
          return;
        }
        this.version.set(v);
        this.explanationKey.set(v.explanationKey ?? '');
        this.changeSummary.set(v.changeSummary ?? '');
        this.advancedJson.set(v.conditionTree);
        this.parseIntoBuilder(v.conditionTree);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load this version.');
        this.loading.set(false);
      },
    });
  }

  private parseIntoBuilder(json: string): void {
    try {
      const parsed = JSON.parse(json);
      let combinator: 'ALL' | 'ANY' = 'ALL';
      let children: unknown[];
      if (Array.isArray(parsed.all)) {
        combinator = 'ALL';
        children = parsed.all;
      } else if (Array.isArray(parsed.any)) {
        combinator = 'ANY';
        children = parsed.any;
      } else if (parsed.fact) {
        combinator = 'ALL';
        children = [parsed];
      } else {
        this.builderMode.set('advanced');
        return;
      }
      const rows: LeafRow[] = [];
      for (const child of children) {
        const c = child as Record<string, unknown>;
        if (!c['fact'] || c['all'] || c['any'] || c['not']) {
          this.builderMode.set('advanced');
          return;
        }
        rows.push({
          fact: String(c['fact']),
          operator: String(c['operator'] ?? 'EQUALS'),
          valueMode: c['threshold'] ? 'threshold' : 'literal',
          value: c['value'] !== undefined ? JSON.stringify(c['value']) : '',
          threshold: c['threshold'] ? String(c['threshold']) : '',
        });
      }
      this.combinator.set(combinator);
      this.leaves.set(rows);
      this.builderMode.set('simple');
    } catch {
      this.builderMode.set('advanced');
    }
  }

  protected operatorsFor(fact: string): string[] {
    return this.facts().find((f) => f.code === fact)?.allowedOperators ?? [];
  }

  protected addLeaf(): void {
    this.leaves.update((rows) => [
      ...rows,
      { fact: this.facts()[0]?.code ?? '', operator: 'EQUALS', valueMode: 'literal', value: '', threshold: '' },
    ]);
  }

  protected removeLeaf(index: number): void {
    this.leaves.update((rows) => rows.filter((_, i) => i !== index));
  }

  protected updateLeaf(index: number, patch: Partial<LeafRow>): void {
    this.leaves.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  /** Builds the canonical condition-tree JSON text from whichever mode is active. */
  protected buildTreeText(): string {
    if (this.builderMode() === 'advanced') {
      return this.advancedJson();
    }
    const leafNodes = this.leaves().map((row) => {
      const node: Record<string, unknown> = { fact: row.fact, operator: row.operator };
      if (row.valueMode === 'threshold' && row.threshold) {
        node['threshold'] = row.threshold;
      } else if (row.value !== '') {
        try {
          node['value'] = JSON.parse(row.value);
        } catch {
          node['value'] = row.value;
        }
      }
      return node;
    });
    if (leafNodes.length === 1) {
      return JSON.stringify(leafNodes[0]);
    }
    return JSON.stringify({ [this.combinator() === 'ALL' ? 'all' : 'any']: leafNodes });
  }

  protected isDraft(): boolean {
    return this.version()?.status === 'DRAFT';
  }

  protected setSection(s: Section): void {
    this.section.set(s);
    if (s === 'history') {
      this.ruleService.reviews(this.code, this.versionNumber).subscribe((r) => this.reviews.set(r));
    }
  }

  protected saveDraft(): void {
    this.actionError.set(null);
    this.ruleService
      .updateDraft(this.code, this.versionNumber, {
        conditionTree: this.buildTreeText(),
        explanationKey: this.explanationKey() || undefined,
        changeSummary: this.changeSummary() || undefined,
      })
      .subscribe({ next: () => this.load(), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected validate(): void {
    this.ruleService.validateTree(this.buildTreeText()).subscribe((v) => this.validation.set(v));
  }

  protected runDryRun(): void {
    this.dryRunError.set(null);
    let facts: Record<string, unknown>;
    try {
      facts = JSON.parse(this.dryRunFactsJson());
    } catch {
      this.dryRunError.set('Facts must be valid JSON, e.g. {"CITIZENSHIP_COUNTRY":"PL"}');
      return;
    }
    this.ruleService
      .dryRun({ conditionTree: this.buildTreeText(), explanationKey: this.explanationKey() || undefined, facts })
      .subscribe({
        next: (result) => this.dryRunResult.set(result),
        error: (e) => this.dryRunError.set(e?.error?.message ?? 'Dry run failed'),
      });
  }

  protected attachSource(): void {
    this.ruleService
      .attachSource(this.code, this.versionNumber, { officialSourceId: this.attachSourceId(), role: this.attachSourceRole() })
      .subscribe({
        next: (v) => {
          this.version.set(v);
          this.attachSourceId.set('');
        },
        error: (e) => this.actionError.set(e?.error?.message ?? 'Could not attach source'),
      });
  }

  protected submit(): void {
    this.ruleService.submit(this.code, this.versionNumber).subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected approve(): void {
    this.ruleService.approve(this.code, this.versionNumber, this.reviewComment()).subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected requestChanges(): void {
    this.ruleService.requestChanges(this.code, this.versionNumber, this.reviewComment()).subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected publish(): void {
    this.ruleService.publish(this.code, this.versionNumber, this.publishEffectiveFrom()).subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected archive(): void {
    this.ruleService.archive(this.code, this.versionNumber).subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }
}
