import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProcedureService, ProcedureSummary } from '../../../core/services/procedure.service';

/**
 * "Browse procedures" - the product's "I know what I need" journey (brief §69), entirely
 * separate from the future "Help me choose" questionnaire (Phase 5-7). No personalized
 * recommendation language here (brief §67) - this is generic public content, sourced entirely
 * from the backend (brief §65: "Do not hard-code procedure cards in Angular").
 */
@Component({
  selector: 'app-procedure-list',
  imports: [RouterLink, MatCardModule, MatChipsModule, MatProgressSpinnerModule],
  templateUrl: './procedure-list.html',
  styleUrl: './procedure-list.scss',
})
export class ProcedureList {
  private readonly procedureService = inject(ProcedureService);

  protected readonly procedures = signal<ProcedureSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.procedureService.getProcedures().subscribe({
      next: (procedures) => {
        this.procedures.set(procedures);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
