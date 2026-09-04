import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { AssessmentService, AssessmentSummary } from '../../core/services/assessment.service';
import { CaseService, CaseSummary } from '../../core/services/case.service';
import { Recommendation, RecommendationService } from '../../core/services/recommendation.service';
import { ProcedureService, ProcedureSummary } from '../../core/services/procedure.service';
import { formatStatusLabel } from '../../shared/status-label.util';

/** A single, human-readable "what should I do next" card - never more than one shown
 * at a time (brief §11). Priority policy (brief §78, chosen and documented, not an
 * unpredictable if/else maze):
 *   1. An active case with a requirement-changed flag ("review changes")
 *   2. An active case with an incomplete checklist ("continue checklist")
 *   3. A completed assessment with no case yet ("view recommendation")
 *   4. An assessment in progress ("continue assessment")
 *   5. No assessment at all ("start assessment") */
interface NextAction {
  heading: string;
  detail: string;
  ctaLabel: string;
  ctaLink: string[];
}

/** Post-MVP UX Milestone UX1 - the real, state-derived core dashboard (brief §9-§21),
 * replacing the previous three-static-cards placeholder. Every section below reflects
 * only the caller's own data (brief §23/§33 - no arbitrary userId, `AuthService`'s
 * `/users/me`-derived state is the only identity involved) and only real backend state
 * - no invented deadlines, no fabricated progress, no eligibility percentage (brief
 * §16/§25/§54/§55). Fetches stay bounded (brief §30/§34): the two list calls always
 * run; at most one further call (an assessment detail for its progress percentage, or
 * the latest recommendation run, or the public procedure list for a brand-new visitor)
 * runs after that, chosen by what the first two calls already revealed - never N+1
 * across every case/assessment.
 */
@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatButtonModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly assessmentService = inject(AssessmentService);
  private readonly caseService = inject(CaseService);
  private readonly recommendationService = inject(RecommendationService);
  private readonly procedureService = inject(ProcedureService);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly assessments = signal<AssessmentSummary[]>([]);
  protected readonly cases = signal<CaseSummary[]>([]);
  protected readonly popularProcedures = signal<ProcedureSummary[]>([]);
  protected readonly assessmentProgressPercent = signal<number | null>(null);
  protected readonly latestRecommendations = signal<Recommendation[]>([]);

  protected readonly isNewUser = computed(() => this.assessments().length === 0 && this.cases().length === 0);

  /** Same "in-progress wins, else most recent completed" precedence the original
   * Phase 5 dashboard used (unchanged logic, still correct). */
  protected readonly relevantAssessment = computed(() => {
    const list = this.assessments();
    return list.find((a) => a.status === 'IN_PROGRESS') ?? list.find((a) => a.status === 'COMPLETED') ?? null;
  });

  private readonly activeStatuses = new Set([
    'DRAFT',
    'PREPARING',
    'READY_TO_SUBMIT',
    'SUBMITTED',
    'WAITING',
    'ADDITIONAL_DOCUMENTS_REQUIRED',
    'DECISION_RECEIVED',
    'APPROVED',
    'REJECTED',
    'APPEAL',
  ]);
  protected readonly activeCases = computed(() => this.cases().filter((c) => this.activeStatuses.has(c.status)));

  /** brief §61 - a bounded subset, not the full case history; "View all cases" links to
   * the real full list. */
  protected readonly visibleActiveCases = computed(() => this.activeCases().slice(0, 4));

  protected readonly casesNeedingAttention = computed(() => this.activeCases().filter((c) => c.hasRequirementUpdates));

  /** brief §12 - only meaningful, non-vanity counters. */
  protected readonly checklistProgressPercent = computed(() => {
    const cases = this.activeCases();
    const stepsTotal = cases.reduce((sum, c) => sum + c.stepsTotal, 0);
    const stepsDone = cases.reduce((sum, c) => sum + c.stepsCompleted, 0);
    if (stepsTotal === 0) {
      return null;
    }
    return Math.round((stepsDone / stepsTotal) * 100);
  });

  /** brief §60 - recent activity derived from data already fetched (case last-updated
   * timestamps), never a separate per-case event-history fetch just for a dashboard
   * summary. */
  protected readonly recentActivity = computed(() =>
    [...this.cases()]
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 5),
  );

  protected readonly primaryMatches = computed(() =>
    this.latestRecommendations().filter((r) => r.recommendationType === 'PRIMARY_MATCH'),
  );

  protected readonly nextAction = computed<NextAction | null>(() => {
    const withUpdates = this.casesNeedingAttention()[0];
    if (withUpdates) {
      return {
        heading: 'Requirements have changed',
        detail: `${withUpdates.procedureTitle} has an updated requirement to review.`,
        ctaLabel: 'Review changes',
        ctaLink: ['/cases', withUpdates.id],
      };
    }

    const incomplete = this.activeCases().find((c) => c.stepsCompleted < c.stepsTotal || c.documentsReady < c.documentsTotal);
    if (incomplete) {
      return {
        heading: 'Continue your checklist',
        detail: `${incomplete.procedureTitle}: ${incomplete.stepsCompleted}/${incomplete.stepsTotal} steps, ${incomplete.documentsReady}/${incomplete.documentsTotal} documents ready.`,
        ctaLabel: 'Open checklist',
        ctaLink: ['/cases', incomplete.id],
      };
    }

    const assessment = this.relevantAssessment();
    if (assessment?.status === 'COMPLETED' && this.cases().length === 0) {
      const primary = this.primaryMatches()[0];
      return {
        heading: primary ? 'Your recommended pathway is ready' : 'Your assessment results are ready',
        detail: primary
          ? `${primary.procedureTitle} appears relevant based on your answers.`
          : 'Review which pathways may be relevant to your situation.',
        ctaLabel: 'View recommendations',
        ctaLink: ['/assessment', assessment.id, 'results'],
      };
    }

    if (assessment?.status === 'IN_PROGRESS') {
      const percent = this.assessmentProgressPercent();
      return {
        heading: 'Continue your assessment',
        detail: percent !== null ? `${percent}% of the visible questions answered.` : 'Pick up where you left off.',
        ctaLabel: 'Continue',
        ctaLink: ['/assessment', assessment.id],
      };
    }

    if (this.assessments().length === 0) {
      return {
        heading: 'Find the right pathway for you',
        detail: 'Answer a few questions to see which procedures may be relevant to your situation.',
        ctaLabel: 'Start assessment',
        ctaLink: ['/assessment/start'],
      };
    }

    return null;
  });

  constructor() {
    forkJoin({
      assessments: this.assessmentService.list(),
      cases: this.caseService.list(),
    }).subscribe({
      next: ({ assessments, cases }) => {
        this.assessments.set(assessments);
        this.cases.set(cases);
        this.loading.set(false);
        this.loadSecondaryData(assessments, cases);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  /** The single, state-chosen follow-up fetch described in this class's own doc
   * comment - never more than one, and never fired for state the primary fetch
   * already ruled out. */
  private loadSecondaryData(assessments: AssessmentSummary[], cases: CaseSummary[]): void {
    const relevant =
      assessments.find((a) => a.status === 'IN_PROGRESS') ?? assessments.find((a) => a.status === 'COMPLETED') ?? null;

    if (relevant?.status === 'IN_PROGRESS') {
      this.assessmentService.get(relevant.id).subscribe({
        next: (detail) => this.assessmentProgressPercent.set(detail.progressPercent),
        error: () => this.assessmentProgressPercent.set(null),
      });
      return;
    }

    if (relevant?.status === 'COMPLETED' && cases.length === 0) {
      this.recommendationService.getLatest(relevant.id).subscribe({
        next: (run) => this.latestRecommendations.set(run.recommendations),
        error: () => this.latestRecommendations.set([]),
      });
      return;
    }

    if (assessments.length === 0 && cases.length === 0) {
      this.procedureService.getProcedures().subscribe({
        next: (procedures) => this.popularProcedures.set(procedures.slice(0, 4)),
        error: () => this.popularProcedures.set([]),
      });
    }
  }

  protected statusLabel(status: string): string {
    return formatStatusLabel(status);
  }

  protected caseProgressPercent(c: CaseSummary): number {
    if (c.stepsTotal === 0) {
      return 0;
    }
    return Math.round((c.stepsCompleted / c.stepsTotal) * 100);
  }

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }

  /** brief §50 - the dashboard error state's "Try again" action. A full reload rather
   * than re-issuing just the two failed HTTP calls - simplest correct fix for a
   * component this size, and consistent with how a real user would recover from "the
   * page failed to load" (unchanged real product behavior, not new complexity to
   * later maintain for an edge case). */
  protected reload(): void {
    window.location.reload();
  }
}
