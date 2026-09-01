import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';

/**
 * Minimal application shell for Phase 1 (docs/architecture/ARCHITECTURE.md §10) - just
 * enough chrome to host routed pages. Real navigation (Residence/Work/Study/... from
 * the Product Requirements home page) arrives with the features that back it, not as
 * placeholder links now.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, MatToolbarModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {}
