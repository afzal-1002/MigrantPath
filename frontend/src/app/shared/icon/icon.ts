import { Component, input } from '@angular/core';

/**
 * Post-MVP UX Milestone UX1 (redesign pass) - self-hosted, inline SVG icons (brief §7).
 * `mat-icon`'s ligature font is unusable here: this app's CSP is `font-src 'self'` and no
 * icon font is bundled (frontend/src/index.html's own Canonical Phase 13 finding - the font
 * was removed because nothing used it). Every icon below is drawn from scratch as a plain
 * 24x24 stroke path - never traced from a third-party icon set - so there is nothing to
 * license and nothing CSP can block.
 */
export type IconName =
  | 'dashboard'
  | 'pathway'
  | 'recommendations'
  | 'cases'
  | 'procedures'
  | 'help'
  | 'account'
  | 'logout'
  | 'admin'
  | 'search'
  | 'bell'
  | 'menu'
  | 'close'
  | 'calendar'
  | 'check-circle'
  | 'alert'
  | 'info'
  | 'chevron-down'
  | 'chevron-right'
  | 'document'
  | 'building'
  | 'eye'
  | 'eye-off'
  | 'plus';

@Component({
  selector: 'app-icon',
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @switch (name()) {
        @case ('dashboard') {
          <rect x="3" y="3" width="7" height="9" rx="1.5" />
          <rect x="14" y="3" width="7" height="5" rx="1.5" />
          <rect x="14" y="12" width="7" height="9" rx="1.5" />
          <rect x="3" y="16" width="7" height="5" rx="1.5" />
        }
        @case ('pathway') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3.5 2" />
        }
        @case ('recommendations') {
          <path
            d="M12 3l1.8 4.6L18.5 9l-4 3 1.1 4.9L12 14.5 8.4 16.9 9.5 12l-4-3 4.7-1.4L12 3z"
          />
        }
        @case ('cases') {
          <path d="M4 8.5a1.5 1.5 0 0 1 1.5-1.5h13a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5v-9z" />
          <path d="M8.5 7V6a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v1" />
        }
        @case ('procedures') {
          <path d="M6 3.5h9.5L20 8v12.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-16a1 1 0 0 1 1-1z" />
          <path d="M15 3.5V8h5" />
          <path d="M8.5 12.5h7M8.5 16h7" />
        }
        @case ('help') {
          <circle cx="12" cy="12" r="9" />
          <path d="M9.6 9.3a2.4 2.4 0 1 1 3.6 2.1c-.9.5-1.2 1-1.2 1.9" />
          <circle cx="12" cy="16.6" r="0.15" fill="currentColor" />
        }
        @case ('account') {
          <circle cx="12" cy="8.2" r="3.4" />
          <path d="M4.8 20c.8-3.6 3.6-5.6 7.2-5.6s6.4 2 7.2 5.6" />
        }
        @case ('logout') {
          <path d="M9 3.5H6a1.5 1.5 0 0 0-1.5 1.5v14A1.5 1.5 0 0 0 6 20.5h3" />
          <path d="M14.5 8L19 12l-4.5 4" />
          <path d="M19 12H9" />
        }
        @case ('admin') {
          <path d="M12 3l7 3v5.5c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z" />
          <path d="M9.3 12.2l1.9 1.9 3.5-3.9" />
        }
        @case ('search') {
          <circle cx="10.8" cy="10.8" r="6.3" />
          <path d="M19.5 19.5l-4-4" />
        }
        @case ('bell') {
          <path d="M6 10a6 6 0 1 1 12 0c0 4 1.5 5.5 1.5 5.5h-15S6 14 6 10z" />
          <path d="M10 18.5a2 2 0 0 0 4 0" />
        }
        @case ('menu') {
          <path d="M4 6.5h16M4 12h16M4 17.5h16" />
        }
        @case ('close') {
          <path d="M5.5 5.5l13 13M18.5 5.5l-13 13" />
        }
        @case ('calendar') {
          <rect x="3.5" y="5" width="17" height="15.5" rx="1.8" />
          <path d="M3.5 9.5h17M8 3v3.5M16 3v3.5" />
        }
        @case ('check-circle') {
          <circle cx="12" cy="12" r="9" />
          <path d="M8 12.3l2.6 2.6L16.2 9" />
        }
        @case ('alert') {
          <path d="M12 3.5L21 19.5H3L12 3.5z" />
          <path d="M12 10v4M12 16.7v.1" />
        }
        @case ('info') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 11v5.3M12 7.7v.1" />
        }
        @case ('chevron-down') {
          <path d="M5.5 9l6.5 6.5L18.5 9" />
        }
        @case ('chevron-right') {
          <path d="M9 5.5L15.5 12 9 18.5" />
        }
        @case ('document') {
          <path d="M6.5 3.5h7l4 4v13a1 1 0 0 1-1 1h-10a1 1 0 0 1-1-1v-16a1 1 0 0 1 1-1z" />
          <path d="M13.5 3.5V7.5h4" />
        }
        @case ('building') {
          <rect x="5" y="3.5" width="10" height="17" rx="0.8" />
          <path d="M15 9.5h4v11h-4M8 7.5h.01M11.5 7.5h.01M8 11h.01M11.5 11h.01M8 14.5h.01M11.5 14.5h.01" />
        }
        @case ('eye') {
          <path d="M3 12s3.5-6.5 9-6.5S21 12 21 12s-3.5 6.5-9 6.5S3 12 3 12z" />
          <circle cx="12" cy="12" r="2.6" />
        }
        @case ('eye-off') {
          <path d="M3 12s3.5-6.5 9-6.5c1.6 0 3 .4 4.2 1M21 12s-1.1 2.1-3.1 3.8M9.9 9.9a2.6 2.6 0 0 0 3.6 3.7" />
          <path d="M6.3 6.3L3 3.5M17.7 17.7L21 20.5" />
        }
        @case ('plus') {
          <path d="M12 5v14M5 12h14" />
        }
      }
    </svg>
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(20);
}
