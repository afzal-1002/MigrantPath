import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { RobotsMetaService } from './core/services/robots-meta.service';

// Post-MVP UX Milestone UX1 - `App` no longer statically wraps every route in one
// global `Shell`. app.routes.ts now defines three sibling top-level route groups
// (public/`Shell`, authenticated/`AppShell`, admin/`AdminShell`), each providing its
// own layout - so the root component's only job is to host whichever one the router
// picks.
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
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
