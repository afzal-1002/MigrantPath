import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Post-MVP UX Milestone UX1 (brief §27/§76) - a small, static help page. Deliberately
 * not a search/ticketing system ("start small... no Zendesk integration required") -
 * short explanations of how the product's own real mechanics work, plus links to the
 * pages that already exist. Public (no auth needed - the topbar/sidebar link to it
 * from the authenticated shell, but a logged-out visitor loses nothing by reaching it
 * directly too).
 */
@Component({
  selector: 'app-help',
  imports: [RouterLink],
  templateUrl: './help.html',
  styleUrl: './help.scss',
})
export class Help {}
