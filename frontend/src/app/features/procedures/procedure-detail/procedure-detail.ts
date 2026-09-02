import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProcedureDetail, ProcedureService } from '../../../core/services/procedure.service';

/**
 * Procedure detail (brief §38/§68). Sections are shown only when non-empty (brief: "do not show
 * empty sections unnecessarily"). Conditional document requirements are labelled carefully (brief
 * §68/§90) - never as if the application has already evaluated the user, since no eligibility
 * engine exists yet.
 */
@Component({
  selector: 'app-procedure-detail',
  imports: [DatePipe, RouterLink, MatChipsModule, MatDividerModule, MatProgressSpinnerModule],
  templateUrl: './procedure-detail.html',
  styleUrl: './procedure-detail.scss',
})
export class ProcedureDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly procedureService = inject(ProcedureService);

  protected readonly procedure = signal<ProcedureDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);

  constructor() {
    const code = this.route.snapshot.paramMap.get('code');
    if (!code) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }
    this.procedureService.getProcedure(code).subscribe({
      next: (procedure) => {
        this.procedure.set(procedure);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  protected requirementTypeLabel(type: string): string {
    switch (type) {
      case 'CONDITIONAL':
        return 'May be required depending on your situation';
      case 'INFORMATIONAL':
        return 'For your information';
      default:
        return 'Required';
    }
  }
}
