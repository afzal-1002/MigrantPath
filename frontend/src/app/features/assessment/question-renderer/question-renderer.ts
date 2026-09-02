import { Component, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { CountrySelect } from '../../../shared/country-select/country-select';
import { QuestionDefinition } from '../../../core/services/assessment.service';

/**
 * The generic question renderer (brief §50) - one component dispatching on {@link
 * QuestionDefinition.answerType}, so adding a new `Question` row changes the wizard with zero
 * Angular code changes (brief §51's DoD). `group` is a two-control {@code FormGroup} (`value` +
 * `unsure`) the wizard builds per visible question - this component never talks to the backend
 * itself, only renders controls and lets the wizard own saving (brief §30: backend is
 * authoritative, this is presentation only).
 *
 * REGION/CITY/DISTRICT questions are not exercised by the seeded MVP questionnaire (brief §11's
 * hierarchical country->region->city->district lookup needs a parent-answer context this schema
 * doesn't yet model) - they fall back to a plain reference-code text input rather than a full
 * cascading picker, a deliberate scope cut documented in PHASE_5_REPORT.md.
 */
@Component({
  selector: 'app-question-renderer',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    MatCheckboxModule,
    CountrySelect,
  ],
  templateUrl: './question-renderer.html',
  styleUrl: './question-renderer.scss',
})
export class QuestionRenderer {
  readonly question = input.required<QuestionDefinition>();
  readonly group = input.required<FormGroup>();

  protected isUnsure(): boolean {
    return this.group().controls['unsure'].value === true;
  }

  /** MULTI_SELECT's control holds a plain `string[]` of selected option codes - membership
   * checked/toggled directly rather than via a per-checkbox FormControl, since the option list
   * itself is dynamic (driven by {@link QuestionDefinition.options}). */
  protected isOptionSelected(code: string): boolean {
    const selected = this.group().controls['value'].value as string[] | null;
    return Array.isArray(selected) && selected.includes(code);
  }

  protected toggleOption(code: string, checked: boolean): void {
    const control = this.group().controls['value'];
    const current: string[] = Array.isArray(control.value) ? [...control.value] : [];
    const next = checked ? [...current.filter((c) => c !== code), code] : current.filter((c) => c !== code);
    control.setValue(next);
    control.markAsDirty();
  }
}
