import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/services/auth.service';
import { AssessmentService, AssessmentSummary } from '../../core/services/assessment.service';

/**
 * Minimal Phase 5 update (brief §56): shows a resume/completed link for the caller's assessment,
 * nothing more - real recommendation content is Phase 7's job. The real immigration-case
 * dashboard is Phase 8's.
 */
@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatButtonModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly assessmentService = inject(AssessmentService);

  protected readonly assessments = signal<AssessmentSummary[]>([]);
  /** Most relevant assessment for the "continue where you left off" card - an in-progress one
   * wins over a completed one, both win over "none yet" (brief §56). */
  protected readonly relevantAssessment = computed(() => {
    const list = this.assessments();
    return list.find((a) => a.status === 'IN_PROGRESS') ?? list.find((a) => a.status === 'COMPLETED') ?? null;
  });

  constructor() {
    this.assessmentService.list().subscribe({
      next: (list) => this.assessments.set(list),
      error: () => this.assessments.set([]),
    });
  }

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
