import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/services/auth.service';

const ADMIN_ROLES = ['CONTENT_EDITOR', 'LEGAL_REVIEWER', 'ADMIN'];

/**
 * Minimal application shell (docs/architecture/ARCHITECTURE.md §10) - just enough
 * chrome to host routed pages and show auth-aware navigation. Real navigation
 * (Residence/Work/Study/... from the Product Requirements home page) arrives with the
 * features that back it, not as placeholder links now.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule],
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
