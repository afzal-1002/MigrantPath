import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';

interface QuickLoginAccount {
  label: string;
  description: string;
  email: string;
  password: string;
}

/** TEMPORARY, explicitly requested by the user for their own manual testing ("just
 * for debugging purposes... I will ask later once app is ready to remove them") -
 * real seeded accounts, not fabricated ones. Every account here was actually
 * registered, verified, and (for "Active case") walked through a real assessment and
 * case creation via the real API - see the session's own record of how each was
 * created. Delete this whole array (and the environment flag it's gated behind) when
 * the user asks. */
const QUICK_LOGIN_ACCOUNTS: QuickLoginAccount[] = [
  {
    label: 'Admin',
    description: 'ADMIN + CONTENT_EDITOR + LEGAL_REVIEWER - full content governance access',
    email: 'qa.admin@example.com',
    password: 'QaAdmin#2026!',
  },
  {
    label: 'QA User',
    description: 'Plain USER role, no case yet - general-purpose test account',
    email: 'qa.user@example.com',
    password: 'QaUser#2026!',
  },
  {
    label: 'Normal User',
    description: 'A genuinely fresh account - nothing done yet, for testing new-user/onboarding states',
    email: 'normal.user@example.com',
    password: 'NormalUser#2026!',
  },
  {
    label: 'User with an active case',
    description: 'Completed assessment + a real "Temporary residence and work" case already in progress',
    email: 'case.demo@example.com',
    password: 'CaseDemo#2026!',
  },
];

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  /** Two independent gates, not one: the build-time environment flag, AND a runtime
   * check that this is actually running on localhost - so this panel can never appear
   * on a real deployed domain even if the environment flag were accidentally left on. */
  protected readonly showQuickLogin =
    environment.debugQuickLoginEnabled &&
    typeof window !== 'undefined' &&
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1');

  protected readonly quickLoginAccounts = QUICK_LOGIN_ACCOUNTS;

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.serverError.set(null);
    const { email, password } = this.form.getRawValue();
    this.loginAs(email, password);
  }

  protected quickLogin(account: QuickLoginAccount): void {
    this.form.setValue({ email: account.email, password: account.password });
    this.submitting.set(true);
    this.serverError.set(null);
    this.loginAs(account.email, account.password);
  }

  private loginAs(email: string, password: string): void {
    this.authService.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigateByUrl('/dashboard');
      },
      error: (err) => {
        this.submitting.set(false);
        // Deliberately shows the backend's own message (INVALID_CREDENTIALS /
        // EMAIL_NOT_VERIFIED / ACCOUNT_LOCKED all have distinct, honest copy - see
        // backend GlobalExceptionHandler) rather than a single generic string.
        this.serverError.set(err?.error?.message ?? 'Sign in failed. Please try again.');
      },
    });
  }
}
