import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CurrentUser {
  id: string;
  email: string;
  firstName: string | null;
  preferredLanguage: string | null;
  emailVerified: boolean;
  roles: string[];
}

export type AuthState = 'UNKNOWN' | 'AUTHENTICATED' | 'UNAUTHENTICATED';

interface MessageResponse {
  message: string;
}

/**
 * Central authentication state (brief §26). `state()` starts at `UNKNOWN` and is
 * resolved once at startup via {@link loadCurrentUser} (wired through an app
 * initializer in app.config.ts) specifically to prevent route-guard flicker - a guard
 * checking `isAuthenticated()` before the initial /users/me call resolves would
 * incorrectly redirect an already-logged-in user to /login on a page reload.
 *
 * Never stores a password, session id, or token (brief §26) - the session cookie is
 * entirely browser/Spring-Security-managed; this service only ever holds the
 * non-sensitive {@link CurrentUser} summary the backend already scrubbed of
 * anything sensitive (see backend CurrentUserResponse).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = `${environment.apiBaseUrl}/auth`;
  private readonly usersBase = `${environment.apiBaseUrl}/users`;

  private readonly state = signal<AuthState>('UNKNOWN');
  private readonly user = signal<CurrentUser | null>(null);

  readonly authState = this.state.asReadonly();
  readonly currentUser = this.user.asReadonly();
  readonly isAuthenticated = computed(() => this.state() === 'AUTHENTICATED');

  /** Called once at startup (see app.config.ts's app initializer). Resolves to
   * UNAUTHENTICATED on a 401 rather than propagating an error - "not logged in" is an
   * expected outcome here, not a failure. */
  loadCurrentUser(): Observable<CurrentUser | null> {
    return new Observable<CurrentUser | null>((subscriber) => {
      this.http.get<CurrentUser>(`${this.usersBase}/me`).subscribe({
        next: (user) => {
          this.user.set(user);
          this.state.set('AUTHENTICATED');
          subscriber.next(user);
          subscriber.complete();
        },
        error: () => {
          this.user.set(null);
          this.state.set('UNAUTHENTICATED');
          subscriber.next(null);
          subscriber.complete();
        },
      });
    });
  }

  register(request: {
    email: string;
    password: string;
    firstName?: string;
    acceptTerms: boolean;
    acceptPrivacyPolicy: boolean;
  }): Observable<CurrentUser> {
    return this.http.post<CurrentUser>(`${this.apiBase}/register`, request);
  }

  login(email: string, password: string): Observable<CurrentUser> {
    return this.http
      .post<CurrentUser>(`${this.apiBase}/login`, { email, password })
      .pipe(
        tap((user) => {
          this.user.set(user);
          this.state.set('AUTHENTICATED');
        }),
      );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.apiBase}/logout`, {}).pipe(
      tap(() => {
        this.user.set(null);
        this.state.set('UNAUTHENTICATED');
      }),
    );
  }

  verifyEmail(token: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiBase}/verify-email`, { token });
  }

  resendVerification(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiBase}/resend-verification`, { email });
  }

  forgotPassword(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiBase}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.apiBase}/reset-password`, { token, newPassword });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.usersBase}/me/change-password`, { currentPassword, newPassword });
  }
}
