import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../core/services/auth.service';
import { Dashboard as DashboardData, DashboardApiService, DashboardNextAction } from '../../core/services/dashboard.service';
import { Icon, IconName } from '../../shared/icon/icon';

const ADMIN_ROLES = ['CONTENT_EDITOR', 'LEGAL_REVIEWER', 'ADMIN'];

interface NavItem {
  label: string;
  icon: IconName;
  path: string;
}

/** brief §7 - the suggested nav list, renamed/reordered from UX1 pass 1's wide-sidebar
 * version ("Overview"→"Dashboard", "Browse procedures"→"Procedures") plus one new item
 * ("Recommendations", whose actual destination is resolved dynamically - see
 * {@link AppShell.recommendationsLink}, since there is no standalone "recommendations"
 * route independent of an assessment). */
const PRIMARY_NAV: NavItem[] = [
  { label: 'Dashboard', icon: 'dashboard', path: '/dashboard' },
  { label: 'Find my pathway', icon: 'pathway', path: '/assessment/start' },
  { label: 'My Cases', icon: 'cases', path: '/cases' },
  { label: 'Procedures', icon: 'procedures', path: '/procedures' },
  { label: 'Help', icon: 'help', path: '/help' },
  { label: 'Account', icon: 'account', path: '/account' },
];

/**
 * Post-MVP UX Milestone UX1 (redesign pass) - the real authenticated application shell,
 * rebuilt around a thin (`--app-rail-width`, 64px) icon-only rail + a slim header,
 * replacing pass 1's wide labeled sidebar (brief §2/§3: "very close to" the reference
 * screenshot's own dense, icon-rail structure). Every icon is self-hosted inline SVG via
 * {@link Icon} - `mat-icon`'s ligature font is unusable under this app's CSP (`font-src
 * 'self'`, no icon font bundled - frontend/src/index.html's own Phase 13 finding).
 *
 * Fetches the same dashboard summary the Dashboard page itself renders (brief §27's
 * "prefer one endpoint" extends here too - the shell reuses it rather than adding a
 * second, shell-only summary call) once per session, purely to drive two small,
 * genuinely data-backed affordances: the header's attention indicator (brief §15 - never
 * a fake badge) and the "Recommendations" nav item's actual target. A failure here never
 * blocks the shell itself - both affordances simply fall back to their empty/default
 * state.
 */
@Component({
  selector: 'app-authenticated-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatMenuModule, MatTooltipModule, Icon],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dashboardApi = inject(DashboardApiService);

  protected readonly navItems = PRIMARY_NAV;
  protected readonly mobileNavOpen = signal(false);

  private readonly dashboardSummary = signal<DashboardData | null>(null);

  constructor() {
    this.dashboardApi.get().subscribe({
      next: (summary) => this.dashboardSummary.set(summary),
      error: () => this.dashboardSummary.set(null),
    });
  }

  /** brief §15 - only real, currently-open "needs attention" items, never a fabricated
   * count. */
  protected readonly attentionActions = computed<DashboardNextAction[]>(
    () => this.dashboardSummary()?.nextActions.filter((a) => a.severity === 'ATTENTION') ?? [],
  );

  /** Recommendations only exist attached to a completed assessment (there is no
   * standalone "my recommendations" list) - this nav item goes to the latest completed
   * assessment's results if one exists, otherwise to starting an assessment. Never an
   * invented "no recommendations" page. */
  protected readonly recommendationsLink = computed<string[]>(() => {
    const status = this.dashboardSummary()?.assessmentStatus;
    if (status?.status === 'COMPLETED') {
      return ['/assessment', status.assessmentId, 'results'];
    }
    return ['/assessment/start'];
  });

  protected actionLink(action: DashboardNextAction): string[] {
    if (action.caseId) {
      return ['/cases', action.caseId];
    }
    if (action.assessmentId) {
      return ['/assessment', action.assessmentId];
    }
    return ['/dashboard'];
  }

  /** Phase 9's own admin-role check (brief §4/§14) - unchanged logic, reused here so the
   * "Administration" icon only ever appears for an account that actually holds one of
   * these roles; independently and authoritatively enforced again server-side regardless. */
  protected readonly isContentAdmin = computed(() =>
    (this.authService.currentUser()?.roles ?? []).some((role) => ADMIN_ROLES.includes(role)),
  );

  protected readonly userInitials = computed(() => {
    const user = this.authService.currentUser();
    const name = user?.firstName?.trim() || user?.email || '';
    return name.slice(0, 1).toUpperCase() || '?';
  });

  protected readonly displayName = computed(
    () => this.authService.currentUser()?.firstName ?? this.authService.currentUser()?.email ?? '',
  );

  protected closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }

  protected onSearchSubmit(term: string): void {
    const trimmed = term.trim();
    this.closeMobileNav();
    this.router.navigate(['/procedures'], trimmed ? { queryParams: { q: trimmed } } : {});
  }

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
