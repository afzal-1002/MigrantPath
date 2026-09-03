import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminReview, AdminReviewService } from '../../../../core/services/admin/admin-review.service';

const ADMIN_ROUTE_SEGMENT: Record<string, string> = {
  PROCEDURE_VERSION: 'procedures',
  RULE_VERSION: 'rules',
  THRESHOLD_VERSION: 'thresholds',
  QUESTIONNAIRE_VERSION: 'questionnaires',
};

/** Route: /admin/reviews - every version currently pending review, across content types (brief §65). */
@Component({
  selector: 'app-review-queue',
  imports: [RouterLink],
  templateUrl: './review-queue.html',
})
export class ReviewQueue {
  private readonly reviewService = inject(AdminReviewService);

  protected readonly reviews = signal<AdminReview[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.reviewService.pendingQueue().subscribe({
      next: (r) => {
        this.reviews.set(r);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected sectionFor(entityType: string): string {
    return ADMIN_ROUTE_SEGMENT[entityType] ?? 'procedures';
  }
}
