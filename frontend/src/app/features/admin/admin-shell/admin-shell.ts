import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

/**
 * Separate admin chrome (brief §15) - kept apart from the ordinary user navigation ({@code
 * layout/shell}), reachable only once a route already behind {@link
 * import('../../../core/guards/admin.guard').adminGuard} has been entered.
 */
@Component({
  selector: 'app-admin-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.scss',
})
export class AdminShell {
  private readonly authService = inject(AuthService);

  protected readonly roles = computed(() => this.authService.currentUser()?.roles ?? []);
  protected readonly isAdmin = computed(() => this.roles().includes('ADMIN'));
}
