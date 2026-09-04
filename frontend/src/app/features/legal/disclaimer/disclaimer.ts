import { Component } from '@angular/core';

/** Phase 11 brief §192 - the "independent informational service, not the Polish
 * government, not legal advice" disclaimer, linked from the other three legal pages
 * and (per CLAUDE.md's own standing rules on never fabricating legal fact) stating
 * plainly that the rules engine, not an AI, is what drives every recommendation. */
@Component({
  selector: 'app-disclaimer',
  imports: [],
  templateUrl: './disclaimer.html',
  styleUrl: '../legal-page.scss',
})
export class Disclaimer {}
