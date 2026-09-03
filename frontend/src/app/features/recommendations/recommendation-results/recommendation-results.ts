import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  Recommendation,
  RecommendationReason,
  RecommendationRun,
  RecommendationService,
} from '../../../core/services/recommendation.service';

interface ApiErrorBody {
  code?: string;
  message?: string;
}

/**
 * "Analyze my pathways" results (Recommendation Engine brief §89-§97) - deliberately cautious
 * language throughout (brief §53): a PRIMARY_MATCH card never says "you qualify," only "appears
 * relevant." Loads the assessment's latest RecommendationRun on entry, auto-triggering the first
 * analysis if none exists yet (brief §88's single-navigation UX) - a distinct "Run analysis
 * again" action (brief §62) always creates a new run rather than mutating the one shown.
 *
 * <p>No i18n framework exists yet anywhere in this app (Phase 5's own scope was English-only) -
 * {@link #reasonText} is a small local fallback mapping from the backend's stable {@code
 * reasonType}/{@code messageKey} to plain English, not a real translation pipeline. A future
 * phase wiring up Angular i18n replaces this with real `messageKey` lookups (brief §54/§103) -
 * documented in PHASE_7_REPORT.md "Deviations".
 */
@Component({
  selector: 'app-recommendation-results',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './recommendation-results.html',
  styleUrl: './recommendation-results.scss',
})
export class RecommendationResults {
  private readonly route = inject(ActivatedRoute);
  private readonly recommendationService = inject(RecommendationService);

  protected readonly assessmentId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly loading = signal(true);
  protected readonly analyzing = signal(false);
  protected readonly run = signal<RecommendationRun | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notCompleted = signal(false);

  protected readonly primaryMatches = computed(() => this.byType('PRIMARY_MATCH'));
  protected readonly alternatives = computed(() => this.byType('POSSIBLE_ALTERNATIVE'));
  protected readonly moreInfoNeeded = computed(() => this.byType('MORE_INFORMATION_REQUIRED'));
  protected readonly otherEvaluated = computed(() =>
    (this.run()?.recommendations ?? []).filter(
      (r) => r.recommendationType === 'NOT_APPLICABLE' || r.recommendationType === 'UNAVAILABLE_FOR_ANALYSIS',
    ),
  );
  protected readonly hasAnyRelevantPathway = computed(
    () => this.primaryMatches().length > 0 || this.alternatives().length > 0 || this.moreInfoNeeded().length > 0,
  );

  constructor() {
    if (!this.assessmentId) {
      this.error.set('No assessment specified.');
      this.loading.set(false);
      return;
    }
    this.loadLatestOrAnalyze();
  }

  private byType(type: Recommendation['recommendationType']): Recommendation[] {
    return (this.run()?.recommendations ?? []).filter((r) => r.recommendationType === type);
  }

  private loadLatestOrAnalyze(): void {
    this.recommendationService.getLatest(this.assessmentId).subscribe({
      next: (run) => {
        this.run.set(run);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ApiErrorBody | undefined;
        if (err.status === 404 && body?.code === 'RECOMMENDATION_RUN_NOT_FOUND') {
          this.analyze();
          return;
        }
        this.handleError(err);
      },
    });
  }

  protected analyze(): void {
    this.analyzing.set(true);
    this.error.set(null);
    this.notCompleted.set(false);
    this.recommendationService.analyze(this.assessmentId).subscribe({
      next: (run) => {
        this.run.set(run);
        this.loading.set(false);
        this.analyzing.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.analyzing.set(false);
        this.handleError(err);
      },
    });
  }

  private handleError(err: HttpErrorResponse): void {
    const body = err.error as ApiErrorBody | undefined;
    if (err.status === 409 && body?.code === 'ASSESSMENT_NOT_COMPLETED') {
      this.notCompleted.set(true);
      return;
    }
    this.error.set('We could not load your results right now. Please try again.');
  }

  protected badgeLabel(type: Recommendation['recommendationType']): string {
    switch (type) {
      case 'PRIMARY_MATCH':
        return 'Most relevant';
      case 'POSSIBLE_ALTERNATIVE':
        return 'Possible alternative';
      case 'MORE_INFORMATION_REQUIRED':
        return 'More information needed';
      case 'NOT_APPLICABLE':
        return 'Does not currently appear applicable';
      case 'UNAVAILABLE_FOR_ANALYSIS':
        return "We couldn't fully analyse this pathway";
    }
  }

  protected reasonText(reason: RecommendationReason): string {
    switch (reason.reasonType) {
      case 'MATCHED_CONDITION':
        return 'A relevant condition was matched.';
      case 'FAILED_CONDITION':
        return 'A required condition was not met.';
      case 'MISSING_INFORMATION':
        return reason.factCode
          ? `We still need to know: ${reason.factCode.replaceAll('_', ' ').toLowerCase()}.`
          : 'Some information is still missing.';
      case 'EXCLUSION':
        return 'A specific circumstance means this pathway does not apply.';
      case 'ALTERNATIVE_PATH':
        return 'This may be relevant as an alternative pathway.';
      case 'PROCEDURE_PRIORITY':
        return 'Another pathway is a closer match to your situation.';
      case 'ANALYSIS_ERROR':
        return "We couldn't fully evaluate this pathway right now.";
    }
  }

  protected reasonIcon(reason: RecommendationReason): string {
    return reason.reasonType === 'MATCHED_CONDITION' ? '✓' : reason.reasonType === 'FAILED_CONDITION' ? '✗' : '!';
  }
}
