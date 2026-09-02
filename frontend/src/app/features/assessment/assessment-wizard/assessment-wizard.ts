import { Component, ElementRef, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, concatMap, of, tap } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import {
  AnswerRequest,
  AnswerType,
  AssessmentDetail,
  AssessmentService,
  MissingQuestion,
  QuestionDefinition,
} from '../../../core/services/assessment.service';
import { QuestionRenderer } from '../question-renderer/question-renderer';
import { QuestionFormValue, isBlank, toAnswerRequest, valueFromAnswer } from '../answer-mapping';

interface ApiErrorBody {
  code?: string;
  message?: string;
  errors?: { field: string; message: string }[];
}

/**
 * Answer types every {@code QuestionDependency} in the seeded MVP questionnaire actually gates on
 * (docs/product/QUESTION_CODES.md) - these autosave and refresh the current section's visible
 * questions immediately on change, so a same-section reveal (e.g. WORK's own {@code
 * HAS_JOB_OFFER} revealing {@code MONTHLY_GROSS_SALARY}, both in the WORK section) shows up
 * without waiting for Next to leave the section entirely. Free-form types (TEXT/INTEGER/DECIMAL/
 * DATE/reference lookups) save on Next/Back only (brief §52), so a mid-typing autosave never
 * steals focus by rebuilding the form underneath the user.
 */
const IMMEDIATE_SAVE_TYPES: ReadonlySet<AnswerType> = new Set(['BOOLEAN', 'SINGLE_SELECT', 'MULTI_SELECT']);

/**
 * The wizard container (brief §48) - backend-authoritative throughout (brief §30): every
 * Next/Back saves the current section's answers, then re-fetches the assessment before deciding
 * which section comes next, so branch changes (brief §29/§53) always come from the server, never
 * predicted client-side. No client-side dependency evaluator exists in this codebase (a deliberate
 * simplification over IMPLEMENTATION_PLAN.md 5.6's "mirror the operator semantics in TypeScript"
 * suggestion - see PHASE_5_REPORT.md "Deviations"): every step transition is one network round
 * trip, trading a little snappiness for a single source of truth on what's visible/required.
 */
@Component({
  selector: 'app-assessment-wizard',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatProgressBarModule, QuestionRenderer],
  templateUrl: './assessment-wizard.html',
  styleUrl: './assessment-wizard.scss',
})
export class AssessmentWizard implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly assessmentService = inject(AssessmentService);

  @ViewChild('stepHeading') private stepHeading?: ElementRef<HTMLElement>;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly detail = signal<AssessmentDetail | null>(null);
  protected readonly currentSectionIndex = signal(0);
  protected readonly showReview = signal(false);
  protected readonly formGroup = signal(new FormGroup<Record<string, FormGroup>>({}));

  protected readonly currentSection = computed(() => this.detail()?.sections[this.currentSectionIndex()] ?? null);
  protected readonly currentSectionQuestions = computed<QuestionDefinition[]>(() => {
    const section = this.currentSection();
    const detail = this.detail();
    if (!section || !detail) {
      return [];
    }
    return detail.questions
      .filter((q) => q.sectionCode === section.code)
      .sort((a, b) => a.sortOrder - b.sortOrder);
  });
  protected readonly isLastSection = computed(() => {
    const detail = this.detail();
    return !!detail && this.currentSectionIndex() >= detail.sections.length - 1;
  });
  protected readonly answeredSections = computed(() => {
    const detail = this.detail();
    if (!detail) {
      return [];
    }
    return detail.sections.map((section) => ({
      section,
      questions: detail.questions
        .filter((q) => q.sectionCode === section.code && q.answer)
        .sort((a, b) => a.sortOrder - b.sortOrder),
    }));
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('No assessment id in the route.');
      this.loading.set(false);
      return;
    }
    this.assessmentService.get(id).subscribe({
      next: (detail) => {
        this.applyDetail(detail);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load this assessment.');
        this.loading.set(false);
      },
    });
  }

  protected next(): void {
    this.saveCurrentSection((fresh) => {
      if (this.currentSectionIndex() < fresh.sections.length - 1) {
        this.currentSectionIndex.update((i) => i + 1);
        this.buildFormForCurrentSection();
        this.focusStepHeading();
      } else {
        this.showReview.set(true);
        this.focusStepHeading();
      }
    });
  }

  protected back(): void {
    this.saveCurrentSection(() => {
      this.currentSectionIndex.update((i) => Math.max(0, i - 1));
      this.buildFormForCurrentSection();
      this.focusStepHeading();
    });
  }

  protected editSection(sectionCode: string): void {
    const detail = this.detail();
    if (!detail) {
      return;
    }
    const index = detail.sections.findIndex((s) => s.code === sectionCode);
    if (index >= 0) {
      this.currentSectionIndex.set(index);
      this.showReview.set(false);
      this.buildFormForCurrentSection();
      this.focusStepHeading();
    }
  }

  protected complete(): void {
    const detail = this.detail();
    if (!detail) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.assessmentService.complete(detail.id).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.applyDetail(updated);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        const body = err.error as ApiErrorBody | undefined;
        if (body?.code === 'ASSESSMENT_INCOMPLETE' && body.errors?.length) {
          this.jumpToFirstMissing(
            body.errors.map((e) => ({ questionCode: e.field, label: e.message, sectionCode: '' })),
          );
          this.error.set('Answer every required question before completing the assessment.');
        } else {
          this.error.set('Could not complete the assessment.');
        }
      },
    });
  }

  protected restart(): void {
    const detail = this.detail();
    if (!detail) {
      return;
    }
    this.assessmentService.restart(detail.id).subscribe({
      next: (created) => {
        void this.router.navigate(['/assessment', created.id]);
      },
    });
  }

  private jumpToFirstMissing(missing: MissingQuestion[]): void {
    const detail = this.detail();
    if (!detail || missing.length === 0) {
      return;
    }
    const question = detail.questions.find((q) => q.questionCode === missing[0].questionCode);
    if (question) {
      this.showReview.set(false);
      this.editSection(question.sectionCode);
    }
  }

  private saveCurrentSection(onDone: (detail: AssessmentDetail) => void): void {
    const questions = this.currentSectionQuestions();
    const group = this.formGroup();
    this.saving.set(true);
    this.error.set(null);

    const detail = this.detail();
    if (!detail) {
      this.saving.set(false);
      return;
    }

    // IMMEDIATE_SAVE_TYPES questions already autosaved on change (see buildFormForCurrentSection) -
    // re-sending them here would just be redundant network traffic (brief §52's "do not create
    // excessive request traffic"), so only the free-form types still need a save-on-Next.
    const toSave = questions.filter((q) => {
      if (IMMEDIATE_SAVE_TYPES.has(q.answerType)) {
        return false;
      }
      const qGroup = group.controls[q.questionCode];
      const formValue: QuestionFormValue = {
        value: qGroup.controls['value'].value,
        unsure: qGroup.controls['unsure'].value,
      };
      return !isBlank(q.answerType, formValue);
    });

    if (toSave.length === 0) {
      this.saving.set(false);
      onDone(detail);
      return;
    }

    let latest = detail;
    of(...toSave)
      .pipe(
        concatMap((question) => {
          const qGroup = group.controls[question.questionCode];
          const request: AnswerRequest = toAnswerRequest(question.answerType, {
            value: qGroup.controls['value'].value,
            unsure: qGroup.controls['unsure'].value,
          });
          return this.assessmentService.saveAnswer(detail.id, question.questionCode, request).pipe(
            tap((updated) => (latest = updated)),
            catchError(() => of(null)),
          );
        }),
      )
      .subscribe({
        complete: () => {
          this.saving.set(false);
          this.applyDetail(latest, /* rebuildForm */ false);
          onDone(latest);
        },
      });
  }

  private applyDetail(detail: AssessmentDetail, rebuildForm = true): void {
    this.detail.set(detail);
    if (rebuildForm) {
      this.buildFormForCurrentSection();
    }
  }

  /**
   * Rebuilds the current section's form. Critically, a question whose control already exists in
   * the *old* form group keeps that control's current live value rather than being reset from
   * the server's `answer` - one control's immediate-save refresh (see {@link
   * IMMEDIATE_SAVE_TYPES}) must never discard a sibling free-form field's not-yet-saved edit
   * (e.g. answering CURRENTLY_IN_POLAND must not blank out an already-typed but not-yet-saved
   * DATE_OF_BIRTH). A newly-revealed question (no existing control) still initializes from the
   * server's answer, same as ever. Moving to a genuinely different section naturally finds no
   * matching old controls (different question codes), so this is safe to apply unconditionally.
   */
  private buildFormForCurrentSection(): void {
    const previous = this.formGroup();
    const group = new FormGroup<Record<string, FormGroup>>({});
    for (const question of this.currentSectionQuestions()) {
      const existing = previous.controls[question.questionCode];
      const initial = existing
        ? { value: existing.controls['value'].value, unsure: existing.controls['unsure'].value }
        : valueFromAnswer(question.answerType, question.answer);
      const questionGroup = new FormGroup({
        value: new FormControl(initial.value),
        unsure: new FormControl(initial.unsure),
      });
      group.addControl(question.questionCode, questionGroup);
      if (IMMEDIATE_SAVE_TYPES.has(question.answerType)) {
        questionGroup.valueChanges.subscribe(() => this.saveOneAnswerAndRefresh(question));
      }
    }
    this.formGroup.set(group);
  }

  /** Saves one BOOLEAN/SINGLE_SELECT/MULTI_SELECT answer as soon as it changes and refreshes the
   * whole assessment (see {@link IMMEDIATE_SAVE_TYPES}'s Javadoc) - the response's fresh question
   * list rebuilds the current section's form, so a newly-revealed same-section question appears
   * immediately. A now-blank value (e.g. every MULTI_SELECT option deselected) is skipped rather
   * than sent, since it would just fail server-side validation while the user is still choosing. */
  private saveOneAnswerAndRefresh(question: QuestionDefinition): void {
    const detail = this.detail();
    const questionGroup = this.formGroup().controls[question.questionCode];
    if (!detail || !questionGroup) {
      return;
    }
    const formValue: QuestionFormValue = {
      value: questionGroup.controls['value'].value,
      unsure: questionGroup.controls['unsure'].value,
    };
    if (isBlank(question.answerType, formValue)) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const request = toAnswerRequest(question.answerType, formValue);
    this.assessmentService.saveAnswer(detail.id, question.questionCode, request).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.applyDetail(updated);
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Could not save your answer. Please try again.');
      },
    });
  }

  /** Human-readable review-page text for one answered question - option/reference codes resolve
   * to their label where the definition carries one (brief §54: never show raw codes to the
   * user). */
  protected reviewAnswerText(question: QuestionDefinition): string {
    const answer = question.answer;
    if (!answer) {
      return '';
    }
    switch (question.answerType) {
      case 'BOOLEAN':
        return answer.booleanValue ? 'Yes' : 'No';
      case 'MULTI_SELECT':
        return (answer.selectedOptionCodes ?? [])
          .map((code) => question.options.find((o) => o.code === code)?.label ?? code)
          .join(', ');
      case 'SINGLE_SELECT':
        return question.options.find((o) => o.code === answer.referenceCode)?.label ?? (answer.referenceCode ?? '');
      case 'INTEGER':
        return answer.integerValue?.toString() ?? '';
      case 'DECIMAL':
        return answer.decimalValue?.toString() ?? '';
      case 'DATE':
        return answer.dateValue ?? '';
      case 'COUNTRY':
      case 'REGION':
      case 'CITY':
      case 'DISTRICT':
        return answer.referenceCode ?? '';
      case 'TEXT':
      default:
        return answer.stringValue ?? '';
    }
  }

  private focusStepHeading(): void {
    queueMicrotask(() => this.stepHeading?.nativeElement.focus());
  }
}
