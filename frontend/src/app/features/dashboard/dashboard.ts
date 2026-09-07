import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CaseStatus } from '../../core/services/case.service';
import { AuthService } from '../../core/services/auth.service';
import {
  Dashboard as DashboardData,
  DashboardApiService,
  DashboardCase,
  DashboardNextAction,
} from '../../core/services/dashboard.service';
import { formatStatusLabel } from '../../shared/status-label.util';
import { Icon, IconName } from '../../shared/icon/icon';

/** severity drives color, never the raw backend string. */
const SEVERITY_ICON: Record<DashboardNextAction['severity'], IconName> = {
  ATTENTION: 'alert',
  NEXT: 'chevron-right',
  INFO: 'info',
};

type AppCardPill = 'filling' | 'pending' | 'review' | 'attention';

/** A plain re-bucketing of this app's own real CaseStatus values into the reference's
 * three-pill vocabulary (plus a fourth, "attention", for a status that's actually a
 * problem) - never a new status this page invents. */
const STATUS_PILL: Record<CaseStatus, AppCardPill> = {
  DRAFT: 'filling',
  PREPARING: 'filling',
  READY_TO_SUBMIT: 'pending',
  SUBMITTED: 'pending',
  WAITING: 'pending',
  ADDITIONAL_DOCUMENTS_REQUIRED: 'attention',
  DECISION_RECEIVED: 'review',
  APPROVED: 'review',
  APPEAL: 'pending',
  REJECTED: 'attention',
  COMPLETED: 'review',
  CANCELLED: 'attention',
};

/** The same 5-stage mapping DashboardService documents on the backend, mirrored here
 * only for display order/icons - the backend's own `stageIndex`/`stageLabel` on
 * `primaryCase` are what's actually shown. */
const TIMELINE_STAGES = ['Started', 'Ready to submit', 'Submitted', 'Decision pending', 'Decision received'];

/** Phase 9's own admin-role check (brief §4/§14), duplicated here rather than shared -
 * same small, independent check AppShell already makes for its own "Administration"
 * rail icon; either one being wrong is still independently and authoritatively
 * enforced server-side regardless. */
const ADMIN_ROLES = ['CONTENT_EDITOR', 'LEGAL_REVIEWER', 'ADMIN'];

/** One tile in the "Quick access" grid (redesign pass 5, corporate-intranet-style
 * shortcut grid reference) - every entry routes to a real page this app already has;
 * `stat`/`caption` are always derived from the same dashboard aggregation the rest of
 * this page uses, never invented (the reference's own fabricated-looking numbers like
 * "Target, 176.00" have no equivalent here - a tile with nothing real to report just
 * shows its static subtitle and no stat). */
interface QuickTile {
  title: string;
  subtitle: string;
  icon: IconName;
  path: string | string[];
  stat?: string;
  caption?: string;
}

/**
 * Post-MVP UX Milestone UX1 (redesign pass 4, "Eviza" dashboard reference): a greeting
 * header, three real stat cards, one process-tracker card for the primary case's real
 * 5-stage progress, a card grid for every real active case ("where it could have
 * possibility for cases" - the grid scales to however many real cases the caller has,
 * not a fixed demo count) plus a real "start a new application" card, and a lower
 * two-panel row (Recent activity / Next actions). Every number/date/status still comes
 * from the single `GET /api/v1/dashboard` aggregation - this component never computes
 * eligibility or invents a deadline. The reference's "Upcoming deadlines" panel (with
 * specific fabricated dates) has no equivalent in this product's real data, so it's
 * replaced with the dashboard's own real, already-existing "Next actions" list rather
 * than invented dates.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, Icon],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dashboardApi = inject(DashboardApiService);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly data = signal<DashboardData | null>(null);

  protected readonly severityIcon = SEVERITY_ICON;
  protected readonly timelineStages = TIMELINE_STAGES;

  protected readonly displayName = computed(
    () => this.authService.currentUser()?.firstName ?? this.authService.currentUser()?.email ?? '',
  );

  protected readonly greeting = computed(() => {
    const hour = new Date().getHours();
    const period = hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening';
    return `Good ${period}, ${this.displayName()}`;
  });

  protected readonly primaryCase = computed<DashboardCase | null>(() => this.data()?.primaryCase ?? null);

  protected readonly userInitials = computed(() => {
    const name = this.data()?.profile.displayName ?? '';
    return name.slice(0, 1).toUpperCase() || '?';
  });

  /** Checklist-completion only (steps + documents), matching the case-detail page's own
   * definition exactly - never a legal-probability or eligibility percentage. */
  protected readonly caseProgressPercent = computed<number>(() => {
    const c = this.primaryCase();
    if (!c) {
      return 0;
    }
    const total = c.stepsTotal + c.documentsTotal;
    if (total === 0) {
      return 0;
    }
    return Math.round(((c.stepsCompleted + c.documentsCompleted) / total) * 100);
  });

  protected readonly trackerSubtitle = computed(() => this.primaryCase()?.authorities[0]?.name ?? null);

  protected readonly activeCaseCount = computed(() => this.data()?.activeCases.length ?? 0);

  /** Stat card 2 ("Action needed") - only genuinely urgent items, never every next
   * action (an onboarding "start assessment" prompt isn't a problem to flag). */
  protected readonly attentionCount = computed(
    () => this.data()?.nextActions.filter((a) => a.severity === 'ATTENTION').length ?? 0,
  );

  protected readonly completedMilestoneCount = computed(() => this.data()?.completedMilestones.length ?? 0);

  protected readonly currentStageIndex = computed(() => this.primaryCase()?.stageIndex ?? null);

  protected readonly isContentAdmin = computed(() =>
    (this.authService.currentUser()?.roles ?? []).some((role) => ADMIN_ROLES.includes(role)),
  );

  /** Same "go to the latest completed assessment's results, else start one" rule the
   * AppShell rail's own Recommendations link uses - there is no standalone "my
   * recommendations" route independent of an assessment. */
  private readonly recommendationsLink = computed<string[]>(() => {
    const status = this.data()?.assessmentStatus;
    if (status?.status === 'COMPLETED') {
      return ['/assessment', status.assessmentId, 'results'];
    }
    return ['/assessment/start'];
  });

  private readonly pathwayCaption = computed(() => {
    const status = this.data()?.assessmentStatus;
    if (!status) {
      return 'Not started';
    }
    if (status.status === 'COMPLETED') {
      return 'Completed';
    }
    return `${status.progressPercent}% complete`;
  });

  private readonly recommendationsCount = computed(() => this.data()?.latestRecommendations.length ?? 0);

  /** The "Quick access" tile grid (redesign pass 5) - real one-click shortcuts to every
   * feature this app actually has, styled after a corporate-intranet shortcut grid
   * (visual pattern only; no unrelated tool names or branding copied - see this file's
   * own {@link QuickTile} doc comment). Recomputed whenever the underlying dashboard
   * data or role changes, same as every other stat on this page. */
  protected readonly quickTiles = computed<QuickTile[]>(() => {
    const activeCases = this.activeCaseCount();
    const recommendations = this.recommendationsCount();
    const tiles: QuickTile[] = [
      {
        title: 'Find my pathway',
        subtitle: 'Guided eligibility assessment',
        icon: 'pathway',
        path: '/assessment/start',
        caption: this.pathwayCaption(),
      },
      {
        title: 'My Cases',
        subtitle: 'Track your applications',
        icon: 'cases',
        path: '/cases',
        stat: String(activeCases),
        caption: activeCases === 1 ? 'active case' : 'active cases',
      },
      {
        title: 'Recommendations',
        subtitle: 'Pathways matched to you',
        icon: 'recommendations',
        path: this.recommendationsLink(),
        stat: recommendations > 0 ? String(recommendations) : undefined,
        caption: recommendations > 0 ? (recommendations === 1 ? 'match' : 'matches') : 'None yet',
      },
      {
        title: 'Procedures',
        subtitle: 'Browse every pathway',
        icon: 'procedures',
        path: '/procedures',
      },
      {
        title: 'Account',
        subtitle: 'Manage your profile',
        icon: 'account',
        path: '/account',
      },
      {
        title: 'Help',
        subtitle: 'Guides & support',
        icon: 'help',
        path: '/help',
      },
    ];
    if (this.isContentAdmin()) {
      tiles.push({
        title: 'Administration',
        subtitle: 'Content governance',
        icon: 'admin',
        path: '/admin',
      });
    }
    return tiles;
  });

  constructor() {
    this.dashboardApi.get().subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected statusLabel(status: string): string {
    return formatStatusLabel(status);
  }

  protected caseSummaryProgressPercent(stepsCompleted: number, documentsReady: number, stepsTotal: number, documentsTotal: number): number {
    const total = stepsTotal + documentsTotal;
    if (total === 0) {
      return 0;
    }
    return Math.round(((stepsCompleted + documentsReady) / total) * 100);
  }

  protected statusPill(status: string): AppCardPill {
    return STATUS_PILL[status as CaseStatus] ?? 'pending';
  }

  protected actionLink(action: DashboardNextAction): string[] {
    if (action.caseId) {
      return ['/cases', action.caseId];
    }
    if (action.assessmentId) {
      return ['/assessment', action.assessmentId];
    }
    return ['/assessment/start'];
  }

  /** "Updated 2 days ago" / "Today, 9:14 AM" / "Yesterday, 4:02 PM" phrasing - a real
   * relative rendering of a real timestamp, never a fabricated one. */
  protected relativeTime(iso: string): string {
    const date = new Date(iso);
    const now = new Date();
    const diffDays = Math.floor((this.startOfDay(now).getTime() - this.startOfDay(date).getTime()) / 86_400_000);
    const time = new DatePipe('en-US').transform(iso, 'shortTime') ?? '';
    if (diffDays <= 0) {
      return `Today, ${time}`;
    }
    if (diffDays === 1) {
      return `Yesterday, ${time}`;
    }
    if (diffDays < 7) {
      return `${diffDays} days ago`;
    }
    return new DatePipe('en-US').transform(iso, 'mediumDate') ?? iso;
  }

  private startOfDay(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }

  protected reload(): void {
    window.location.reload();
  }
}
