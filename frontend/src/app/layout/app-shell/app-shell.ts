import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatListModule } from '@angular/material/list';
import { map } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';

const ADMIN_ROLES = ['CONTENT_EDITOR', 'LEGAL_REVIEWER', 'ADMIN'];

interface NavItem {
  label: string;
  path: string;
}

// No mat-icon here, deliberately - this codebase's CSP (font-src 'self') already
// blocks the third-party font `mat-icon`'s ligature usage would need, and the icon
// font was removed in an earlier phase specifically because nothing used it
// (frontend/src/index.html's own real finding, Canonical Phase 13). Wayfinding
// instead comes from clear labels + a strong active-route visual treatment
// (app-shell.scss) - the same text-only pattern the existing admin shell already
// uses successfully.
const PRIMARY_NAV: NavItem[] = [
  { label: 'Overview', path: '/dashboard' },
  { label: 'Find my pathway', path: '/assessment/start' },
  { label: 'My cases', path: '/cases' },
  { label: 'Browse procedures', path: '/procedures' },
  { label: 'Account', path: '/account' },
  { label: 'Help', path: '/help' },
];

/**
 * Post-MVP UX Milestone UX1 - the real authenticated application shell (brief §3):
 * a persistent left sidebar + a top bar, replacing the previous "disconnected pages
 * behind one thin toolbar" experience for every logged-in route. Deliberately
 * separate from both the public {@link import('../shell/shell').Shell} (anonymous
 * pages keep their own simple toolbar - no reason to show case/account navigation to
 * a visitor who isn't logged in) and the existing admin shell (brief §45 - "user
 * dashboard != admin dashboard," kept as two genuinely separate layouts).
 *
 * Sidebar behavior (brief §5): permanent/"side" mode at desktop widths, an overlay
 * drawer ("over" mode, closed by default) below ~900px - `BreakpointObserver` decides
 * which, not a CSS-only trick, so the drawer's open/close state and backdrop actually
 * work correctly on mobile.
 */
@Component({
  selector: 'app-authenticated-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatButtonModule,
    MatMenuModule,
    MatListModule,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly breakpointObserver = inject(BreakpointObserver);

  protected readonly navItems = PRIMARY_NAV;

  /** brief §5 - "over" (overlay drawer, closed by default) below ~900px, "side"
   * (permanent, always open) at desktop widths. `Breakpoints.Handset`/`.Tablet` alone
   * are portrait-orientation-biased in a way that doesn't match this app's own actual
   * layout needs, so an explicit max-width query is used instead. */
  private readonly isCompact = toSignal(
    this.breakpointObserver.observe('(max-width: 900px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly sidenavMode = computed(() => (this.isCompact() ? 'over' : 'side'));
  protected readonly sidenavOpened = computed(() => !this.isCompact());

  /** Phase 9's own admin-role check (brief §4/§14) - unchanged logic, reused here so the
   * "Administration" section only ever appears for an account that actually holds one of
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

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
