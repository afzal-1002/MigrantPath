import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { map } from 'rxjs';
import { ProcedureService, ProcedureSummary } from '../../../core/services/procedure.service';
import { Icon } from '../../../shared/icon/icon';

/**
 * "Browse procedures" - the product's "I know what I need" journey (brief §69), entirely
 * separate from the future "Help me choose" questionnaire (Phase 5-7). No personalized
 * recommendation language here (brief §67) - this is generic public content, sourced entirely
 * from the backend (brief §65: "Do not hard-code procedure cards in Angular").
 *
 * Post-MVP UX Milestone UX1 (redesign pass) - a real client-side name/summary filter (brief §26:
 * the shell's header search needs somewhere genuine to land), seeded from an optional `?q=`
 * query param so the shell's search box can deep-link here, and editable on the page itself.
 * Never a fabricated "search index" - it filters the same, already-fetched, backend-sourced
 * list every visitor already sees.
 */
@Component({
  selector: 'app-procedure-list',
  imports: [RouterLink, MatCardModule, MatChipsModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule, Icon],
  templateUrl: './procedure-list.html',
  styleUrl: './procedure-list.scss',
})
export class ProcedureList {
  private readonly procedureService = inject(ProcedureService);
  private readonly route = inject(ActivatedRoute);

  protected readonly procedures = signal<ProcedureSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  private readonly initialQuery = toSignal(this.route.queryParamMap.pipe(map((params) => params.get('q') ?? '')), {
    initialValue: '',
  });
  protected readonly query = signal('');

  protected readonly filteredProcedures = computed(() => {
    const term = this.query().trim().toLowerCase();
    if (!term) {
      return this.procedures();
    }
    return this.procedures().filter(
      (p) => p.name.toLowerCase().includes(term) || (p.summary ?? '').toLowerCase().includes(term),
    );
  });

  constructor() {
    this.query.set(this.initialQuery());
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

  protected onQueryInput(value: string): void {
    this.query.set(value);
  }
}
