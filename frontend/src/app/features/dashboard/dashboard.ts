import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/services/auth.service';

/**
 * Exists only to prove authenticated navigation works end to end (brief §29) - the
 * real immigration-case dashboard is Phase 8's job. Nothing immigration-specific
 * belongs here yet.
 */
@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatButtonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/login'));
  }
}
