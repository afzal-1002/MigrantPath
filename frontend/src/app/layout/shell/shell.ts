import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/services/auth.service';

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

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
