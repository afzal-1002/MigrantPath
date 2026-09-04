import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Phase 11 brief §192 - draft privacy page. Content traces to
 * docs/privacy/DATA_INVENTORY.md and docs/privacy/RETENTION_POLICY.md - every
 * category named here is a real table/column, not generic privacy-policy boilerplate.
 * Honestly marked as a draft, not attorney-reviewed (brief's own explicit instruction
 * not to fake legal certainty here).
 */
@Component({
  selector: 'app-privacy-policy',
  imports: [RouterLink],
  templateUrl: './privacy-policy.html',
  styleUrl: '../legal-page.scss',
})
export class PrivacyPolicy {}
