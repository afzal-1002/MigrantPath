import { Component, inject } from '@angular/core';
import { Shell } from './layout/shell/shell';
import { RobotsMetaService } from './core/services/robots-meta.service';

@Component({
  selector: 'app-root',
  imports: [Shell],
  template: '<app-shell />',
})
export class App {
  // Phase 11 brief §92/§93 - starts the per-navigation noindex meta-tag handling; see
  // RobotsMetaService for what it actually does and why it lives here (the one place
  // guaranteed to exist for the lifetime of the app, regardless of route).
  private readonly robotsMeta = inject(RobotsMetaService);

  constructor() {
    this.robotsMeta.init();
  }
}
