import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AssessmentService } from '../../../core/services/assessment.service';

/**
 * `/assessment/start` - starts (or resumes, brief §33) the caller's assessment and immediately
 * redirects to `/assessment/:id`, the real wizard route. Kept as its own tiny route rather than
 * folding the POST into the wizard component itself, so a plain link ("Help me choose") never
 * needs to know an assessment id in advance.
 */
@Component({
  selector: 'app-assessment-start',
  template: `
    @if (error()) {
      <p role="alert">{{ error() }}</p>
    } @else {
      <p role="status">Starting your assessment...</p>
    }
  `,
})
export class AssessmentStart implements OnInit {
  private readonly assessmentService = inject(AssessmentService);
  private readonly router = inject(Router);

  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.assessmentService.start().subscribe({
      next: (detail) => {
        void this.router.navigate(['/assessment', detail.id]);
      },
      error: () => {
        this.error.set('Could not start the assessment. Please try again.');
      },
    });
  }
}
