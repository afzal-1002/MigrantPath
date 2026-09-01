import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { PlatformStatusService } from '../../core/services/platform-status.service';

/**
 * Phase 1 placeholder home page (docs/architecture/ARCHITECTURE.md §10). Its only
 * job right now is proving frontend↔backend connectivity (brief §14); the real
 * "Help me choose" / "Browse procedures" landing experience (Product Requirements §5)
 * arrives with the assessment/procedure features in later phases.
 */
@Component({
  selector: 'app-home',
  imports: [MatCardModule],
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
