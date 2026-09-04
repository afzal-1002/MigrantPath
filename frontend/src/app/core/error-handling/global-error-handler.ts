import { ErrorHandler, Injectable } from '@angular/core';

/**
 * Canonical Phase 14 (Observability) brief §40/§43/§69 - the one, clearly-labeled place an
 * unhandled Angular error (a thrown exception during change detection/an event handler, or an
 * unhandled promise rejection - both already funnelled here by `provideBrowserGlobalErrorListeners()`
 * in app.config.ts) is caught. No external error-tracking service is wired this phase (no Sentry
 * account/DSN exists - see docs/operations/ERROR_TRACKING.md, status `DOCUMENTED_ONLY`) - this
 * class is the exact, real integration boundary a future `Sentry.captureException(error)` call
 * would go in, replacing nothing else in the app (brief §138 - "if selected tracker unreachable,
 * app continues... no user request blocked", satisfied trivially today since nothing is called
 * out yet).
 *
 * <p>Never logs anything about the *user* (brief §41/§43 - no email, no form values, no request
 * payload) - only the {@link Error} object itself, which is developer/operational content
 * (a stack trace pointing at application code), the same thing Angular's own default
 * {@link ErrorHandler} already prints today. This class changes *where* that happens (one
 * explicit, testable class instead of the framework default) and gives a single point to extend
 * later, not what gets logged.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    // Deliberately still logs in production too (brief §69's "no verbose console
    // logging in production" is about *routine* debug chatter - an actually-unhandled
    // error has nowhere else to go today with no tracker wired, and silently
    // swallowing it would be strictly worse for diagnosability than one console
    // entry). Kept to a single call, no extra request/state dump.
    console.error('Unhandled application error', error);

    // Integration point for a future error-tracking SDK (brief §40/§42/§43), e.g.:
    //   Sentry.captureException(error);
    // Not implemented: no provider account/DSN exists yet
    // (docs/operations/ERROR_TRACKING.md).
  }
}
