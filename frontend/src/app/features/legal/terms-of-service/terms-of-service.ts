import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Phase 11 brief §192 - draft terms page. See privacy-policy.ts for the same
 * "draft, not attorney-reviewed" framing this whole legal/ group shares. */
@Component({
  selector: 'app-terms-of-service',
  imports: [RouterLink],
  templateUrl: './terms-of-service.html',
  styleUrl: '../legal-page.scss',
})
export class TermsOfService {}
