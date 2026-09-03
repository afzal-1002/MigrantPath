import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AdminDocument,
  AdminProcedureVersionDetail,
  AdminProcedureService,
  AdminReview,
  ValidationResult,
} from '../../../../core/services/admin/admin-procedure.service';

type Section = 'overview' | 'steps' | 'documents' | 'fees' | 'sources' | 'validation' | 'history';

/**
 * Route: /admin/procedures/:code/versions/:versionNumber - the full DRAFT editor (brief §19).
 * Sections are toggled in one component rather than separate routed tab components (a deliberate
 * scope simplification - see PHASE_9_REPORT.md).
 */
@Component({
  selector: 'app-procedure-version-editor',
  imports: [RouterLink, FormsModule],
  templateUrl: './procedure-version-editor.html',
})
export class ProcedureVersionEditor {
  private readonly route = inject(ActivatedRoute);
  private readonly procedureService = inject(AdminProcedureService);

  protected readonly code = this.route.snapshot.paramMap.get('code')!;
  protected readonly versionNumber = Number(this.route.snapshot.paramMap.get('versionNumber'));

  protected readonly version = signal<AdminProcedureVersionDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly section = signal<Section>('overview');
  protected readonly validation = signal<ValidationResult | null>(null);
  protected readonly reviews = signal<AdminReview[]>([]);

  protected readonly overviewTitle = signal('');
  protected readonly overviewSummary = signal('');
  protected readonly overviewDescription = signal('');
  protected readonly overviewEffectiveFrom = signal('');
  protected readonly overviewChangeSummary = signal('');

  protected readonly newStepCode = signal('');
  protected readonly newStepTitle = signal('');
  protected readonly newStepType = signal('PREPARATION');
  protected readonly newStepSortOrder = signal(1);
  protected readonly newStepMandatory = signal(true);

  protected readonly newDocCode = signal('');
  protected readonly newDocName = signal('');
  protected readonly newDocType = signal('DEFAULT_REQUIRED');
  protected readonly newDocSortOrder = signal(1);

  protected readonly newFeeCode = signal('');
  protected readonly newFeeType = signal('APPLICATION');
  protected readonly newFeeAmount = signal(0);
  protected readonly newFeeCurrency = signal('PLN');

  protected readonly attachSourceId = signal('');
  protected readonly attachSourceRole = signal('PRIMARY');

  protected readonly publishEffectiveFrom = signal('');
  protected readonly reviewComment = signal('');
  protected readonly actionError = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected setSection(s: Section): void {
    this.section.set(s);
    if (s === 'validation') {
      this.procedureService.validate(this.code, this.versionNumber).subscribe((v) => this.validation.set(v));
    }
    if (s === 'history') {
      this.procedureService.reviews(this.code, this.versionNumber).subscribe((r) => this.reviews.set(r));
    }
  }

  private load(): void {
    this.loading.set(true);
    this.procedureService.versionDetail(this.code, this.versionNumber).subscribe({
      next: (v) => {
        this.version.set(v);
        this.overviewTitle.set(v.title);
        this.overviewSummary.set(v.summary ?? '');
        this.overviewDescription.set(v.description ?? '');
        this.overviewEffectiveFrom.set(v.effectiveFrom ?? '');
        this.overviewChangeSummary.set(v.changeSummary ?? '');
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load this version.');
        this.loading.set(false);
      },
    });
  }

  protected isDraft(): boolean {
    return this.version()?.status === 'DRAFT';
  }

  protected saveOverview(): void {
    this.actionError.set(null);
    this.procedureService
      .updateOverview(this.code, this.versionNumber, {
        title: this.overviewTitle(),
        summary: this.overviewSummary(),
        description: this.overviewDescription(),
        effectiveFrom: this.overviewEffectiveFrom() || undefined,
        changeSummary: this.overviewChangeSummary(),
      })
      .subscribe({ next: (v) => this.version.set(v), error: () => this.actionError.set('Could not save overview') });
  }

  protected addStep(): void {
    this.procedureService
      .addStep(this.code, this.versionNumber, {
        stableCode: this.newStepCode(),
        title: this.newStepTitle(),
        stepType: this.newStepType(),
        sortOrder: this.newStepSortOrder(),
        mandatory: this.newStepMandatory(),
      })
      .subscribe({
        next: () => {
          this.newStepCode.set('');
          this.newStepTitle.set('');
          this.load();
        },
        error: (err) => this.actionError.set(err?.error?.message ?? 'Could not add step'),
      });
  }

  protected removeStep(stepId: string): void {
    this.procedureService.removeStep(this.code, this.versionNumber, stepId).subscribe((v) => this.version.set(v));
  }

  protected addDocument(): void {
    this.procedureService
      .addDocument(this.code, this.versionNumber, {
        stableCode: this.newDocCode(),
        name: this.newDocName(),
        requirementType: this.newDocType(),
        requiredByDefault: true,
        sortOrder: this.newDocSortOrder(),
      })
      .subscribe({
        next: () => {
          this.newDocCode.set('');
          this.newDocName.set('');
          this.load();
        },
        error: (err) => this.actionError.set(err?.error?.message ?? 'Could not add document'),
      });
  }

  protected updateDocumentType(doc: AdminDocument, requirementType: string): void {
    this.procedureService
      .updateDocument(this.code, this.versionNumber, doc.id, { ...doc, requirementType })
      .subscribe((v) => this.version.set(v));
  }

  protected removeDocument(documentId: string): void {
    this.procedureService
      .removeDocument(this.code, this.versionNumber, documentId)
      .subscribe((v) => this.version.set(v));
  }

  protected addFee(): void {
    this.procedureService
      .addFee(this.code, this.versionNumber, {
        stableCode: this.newFeeCode(),
        feeType: this.newFeeType(),
        amount: this.newFeeAmount(),
        currency: this.newFeeCurrency(),
      })
      .subscribe({
        next: (v) => {
          this.version.set(v);
          this.newFeeCode.set('');
        },
        error: (err) => this.actionError.set(err?.error?.message ?? 'Could not add fee'),
      });
  }

  protected removeFee(feeId: string): void {
    this.procedureService.removeFee(this.code, this.versionNumber, feeId).subscribe((v) => this.version.set(v));
  }

  protected attachSource(): void {
    this.procedureService
      .attachSource(this.code, this.versionNumber, { officialSourceId: this.attachSourceId(), role: this.attachSourceRole() })
      .subscribe({
        next: () => {
          this.attachSourceId.set('');
          this.load();
        },
        error: (err) => this.actionError.set(err?.error?.message ?? 'Could not attach source - check the source id'),
      });
  }

  protected submit(): void {
    this.actionError.set(null);
    this.procedureService
      .submit(this.code, this.versionNumber)
      .subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected approve(): void {
    this.actionError.set(null);
    this.procedureService
      .approve(this.code, this.versionNumber, this.reviewComment())
      .subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected requestChanges(): void {
    this.actionError.set(null);
    this.procedureService
      .requestChanges(this.code, this.versionNumber, this.reviewComment())
      .subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected publish(): void {
    this.actionError.set(null);
    this.procedureService
      .publish(this.code, this.versionNumber, this.publishEffectiveFrom())
      .subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }

  protected archive(): void {
    this.actionError.set(null);
    this.procedureService
      .archive(this.code, this.versionNumber)
      .subscribe({ next: (v) => this.version.set(v), error: (e) => this.actionError.set(e?.error?.message) });
  }
}
