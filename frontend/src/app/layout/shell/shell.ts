import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { Icon } from '../../shared/icon/icon';

const ADMIN_ROLES = ['CONTENT_EDITOR', 'LEGAL_REVIEWER', 'ADMIN'];

/**
 * Public/anonymous application shell (docs/architecture/ARCHITECTURE.md §10).
 *
 * UI redesign (a real government immigration-portal reference, visual style only): a
 * dark banner + underlined nav tabs + a thin top utility bar, adapted honestly rather
 * than copied - this is an independent, informational service (every legal page says
 * so explicitly), not a government body, so the reference's own department crest,
 * exact name, and green are deliberately not reproduced; this uses this app's own
 * brand mark and the shared --app-accent-teal token instead. The reference's
 * "Choose Your Language" dropdown and settings gear are also left out - this app has
 * no working i18n or user-settings page for either to actually control, and a
 * decorative control that does nothing is exactly the kind of dead UI this codebase
 * has consistently avoided all session. Real conditional nav (Login/Register vs
 * Dashboard/Account/Admin/Logout) is unchanged logic, only restyled.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /** Phase 9 (brief §14) - the admin nav link is only ever shown to an account holding one of
   * the admin roles; unauthorized navigation is still independently blocked by adminGuard and,
   * authoritatively, by every admin API endpoint's own SecurityConfig matcher. */
  protected readonly isContentAdmin = computed(() =>
    (this.authService.currentUser()?.roles ?? []).some((role) => ADMIN_ROLES.includes(role)),
  );

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
