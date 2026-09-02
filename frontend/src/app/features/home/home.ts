import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { PlatformStatusService } from '../../core/services/platform-status.service';

/**
 * The two entry points Product Requirements §5 requires (docs/architecture/ARCHITECTURE.md §10):
 * "Help me choose" (the questionnaire, brief §2) and "Browse procedures" (Phase 4's independent
 * category-tree path) - the questionnaire is never framed as "which permit do you want" (brief
 * §2's explicit non-goal). "Help me choose" routes through {@code authGuard} (Phase 5 is
 * authenticated-only, brief §32) - an anonymous visitor is redirected to log in first, same as any
 * other protected route.
 */
@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatButtonModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly platformStatusService = inject(PlatformStatusService);

  protected readonly connectionState = signal<'checking' | 'connected' | 'unreachable'>(
    'checking',
  );
  protected readonly backendVersion = signal<string | null>(null);

  constructor() {
    this.platformStatusService.getStatus().subscribe({
      next: (status) => {
        this.connectionState.set('connected');
        this.backendVersion.set(status.version);
      },
      error: () => this.connectionState.set('unreachable'),
    });
  }
}
