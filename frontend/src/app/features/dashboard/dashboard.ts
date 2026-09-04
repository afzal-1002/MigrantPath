import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  Dashboard as DashboardData,
  DashboardApiService,
  DashboardCase,
  DashboardNextAction,
} from '../../core/services/dashboard.service';
import { formatStatusLabel } from '../../shared/status-label.util';
import { Icon, IconName } from '../../shared/icon/icon';

/** brief §31/§50 - severity drives color, never the raw backend string. */
const SEVERITY_ICON: Record<DashboardNextAction['severity'], IconName> = {
  ATTENTION: 'alert',
  NEXT: 'chevron-right',
  INFO: 'info',
};

interface CalendarDay {
  date: Date | null;
  isToday: boolean;
  hasEvent: boolean;
}

interface CalendarMonth {
  label: string;
  weeks: CalendarDay[][];
}

interface HelpTopic {
  label: string;
  link: string;
}

const HELP_TOPICS: HelpTopic[] = [
  { label: 'How recommendations work', link: '/help' },
  { label: 'How case checklists work', link: '/help' },
  { label: 'Why official sources matter', link: '/help' },
  { label: 'Privacy and your data', link: '/privacy' },
];

/** brief §12/§13 - the same 5-stage mapping DashboardService documents on the backend,
 * mirrored here only for display labels/order (the backend's own `stageIndex`/
 * `stageLabel` on `primaryCase` are what's actually shown - this array exists only so
 * the timeline can render every stage, not only the reached ones). */
const TIMELINE_STAGES = ['Started', 'Ready to submit', 'Submitted', 'Decision pending', 'Decision received'];

/**
 * Post-MVP UX Milestone UX1 (redesign pass) - the dense, 42-Intra-inspired authenticated
 * dashboard (brief §1-§40), replacing pass 1's generic SaaS four-KPI-cards layout. Every
 * number, date, and status here comes straight from the single {@code GET /api/v1/dashboard}
 * aggregation ({@link DashboardApiService}) - this component never computes eligibility,
 * invents a deadline, or shows a raw backend enum (brief §41-§55). The same structural shell
 * (hero, timeline, 3x2 card grid) renders for every user state - new user, assessment in
 * progress, recommendation ready, one active case, or several - only the content inside each
 * region changes (brief §56-§60).
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, Icon, DatePipe],
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
  protected readonly helpQuery = signal('');

  protected readonly severityIcon = SEVERITY_ICON;
  protected readonly timelineStages = TIMELINE_STAGES;

  protected readonly displayName = computed(
    () => this.authService.currentUser()?.firstName ?? this.authService.currentUser()?.email ?? '',
  );

  protected readonly primaryCase = computed<DashboardCase | null>(() => this.data()?.primaryCase ?? null);

  protected readonly userInitials = computed(() => {
    const name = this.data()?.profile.displayName ?? '';
    return name.slice(0, 1).toUpperCase() || '?';
  });

  /** brief §17 - checklist completion only (steps + documents), never a legal-probability
   * or eligibility percentage. Fees are tracked separately (mini-bar) since an unpaid fee
   * doesn't mean a step wasn't completed. */
  protected readonly caseProgressPercent = computed<number | null>(() => {
    const c = this.primaryCase();
    if (!c) {
      return null;
    }
    const total = c.stepsTotal + c.documentsTotal;
    if (total === 0) {
      return null;
    }
    return Math.round(((c.stepsCompleted + c.documentsCompleted) / total) * 100);
  });

  protected readonly primaryPathwayTitle = computed<string | null>(() => {
    const c = this.primaryCase();
    if (c) {
      return c.procedureTitle;
    }
    const primaryMatch = this.data()?.latestRecommendations.find((r) => r.recommendationType === 'PRIMARY_MATCH');
    return primaryMatch?.procedureTitle ?? this.data()?.latestRecommendations[0]?.procedureTitle ?? null;
  });

  protected readonly activeCaseCount = computed(() => this.data()?.activeCases.length ?? 0);
  protected readonly openActionCount = computed(() => this.data()?.nextActions.length ?? 0);

  protected readonly currentStageIndex = computed(() => this.primaryCase()?.stageIndex ?? null);

  protected readonly primaryAuthority = computed(() => this.primaryCase()?.authorities[0] ?? null);
  protected readonly primaryOffice = computed(() => this.primaryCase()?.offices[0] ?? null);

  protected readonly visibleHelpTopics = computed(() => {
    const term = this.helpQuery().trim().toLowerCase();
    if (!term) {
      return HELP_TOPICS;
    }
    return HELP_TOPICS.filter((t) => t.label.toLowerCase().includes(term));
  });

  /** brief §35 - a compact, real, 2-month calendar; a day is only ever marked if it
   * matches one of the backend's own `importantDates` (never a fabricated deadline). */
  protected readonly calendarMonths = computed<CalendarMonth[]>(() => {
    const importantDays = new Set((this.data()?.importantDates ?? []).map((d) => this.dayKey(new Date(d.date))));
    const today = new Date();
    return [0, 1].map((offset) => this.buildMonth(today.getFullYear(), today.getMonth() + offset, importantDays));
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

  protected caseSummaryProgressPercent(stepsCompleted: number, stepsTotal: number): number {
    if (stepsTotal === 0) {
      return 0;
    }
    return Math.round((stepsCompleted / stepsTotal) * 100);
  }

  protected onHelpQueryInput(value: string): void {
    this.helpQuery.set(value);
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

  private dayKey(date: Date): string {
    return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
  }

  private buildMonth(year: number, month: number, importantDays: Set<string>): CalendarMonth {
    const normalizedYear = year + Math.floor(month / 12);
    const normalizedMonth = ((month % 12) + 12) % 12;
    const firstOfMonth = new Date(normalizedYear, normalizedMonth, 1);
    const daysInMonth = new Date(normalizedYear, normalizedMonth + 1, 0).getDate();
    // Monday-first grid (ISO-style, matching this codebase's other date displays).
    const leadingBlanks = (firstOfMonth.getDay() + 6) % 7;
    const today = new Date();

    const days: CalendarDay[] = [];
    for (let i = 0; i < leadingBlanks; i++) {
      days.push({ date: null, isToday: false, hasEvent: false });
    }
    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(normalizedYear, normalizedMonth, day);
      days.push({
        date,
        isToday:
          date.getFullYear() === today.getFullYear() && date.getMonth() === today.getMonth() && date.getDate() === today.getDate(),
        hasEvent: importantDays.has(this.dayKey(date)),
      });
    }
    while (days.length % 7 !== 0) {
      days.push({ date: null, isToday: false, hasEvent: false });
    }

    const weeks: CalendarDay[][] = [];
    for (let i = 0; i < days.length; i += 7) {
      weeks.push(days.slice(i, i + 7));
    }

    return {
      label: firstOfMonth.toLocaleDateString(undefined, { month: 'long', year: 'numeric' }),
      weeks,
    };
  }

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }

  protected reload(): void {
    window.location.reload();
  }
}
