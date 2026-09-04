import { Injectable, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * Phase 11 brief §92/§93 - keeps private/no-value-to-index routes (auth flows, the
 * authenticated dashboard/assessment/cases/admin areas, the dev-only reference demo)
 * out of search results with a real `<meta name="robots" content="noindex">` tag,
 * complementing the backend's own public-route allowlist in `SitemapController`
 * (that controller only ever *lists* the genuinely public routes - it doesn't stop a
 * crawler that discovers a private URL some other way, e.g. a followed link).
 *
 * A route (or any of its ancestors, so marking a parent like `/admin` covers every
 * child route) opts in via `data: { noIndex: true }` in `app.routes.ts`. This is a
 * client-side courtesy for well-behaved crawlers, not an access control - every one of
 * these routes is independently guarded server-side (401/403) and client-side
 * (authGuard/adminGuard) regardless of whether it's indexed.
 */
@Injectable({ providedIn: 'root' })
export class RobotsMetaService {
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly meta = inject(Meta);

  init(): void {
    this.router
      .events.pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.applyRobotsMeta());
    // Also apply on the very first load (NavigationEnd only fires on subsequent
    // navigations relative to when this subscription starts in some timing orders).
    this.applyRobotsMeta();
  }

  private applyRobotsMeta(): void {
    let route: ActivatedRoute | null = this.activatedRoute.root;
    let noIndex = false;
    while (route) {
      if (route.snapshot.data['noIndex']) {
        noIndex = true;
        break;
      }
      route = route.firstChild;
    }

    if (noIndex) {
      this.meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
    } else {
      this.meta.removeTag("name='robots'");
    }
  }
}
