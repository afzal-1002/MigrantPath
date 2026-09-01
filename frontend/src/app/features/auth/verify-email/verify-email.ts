import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../../core/services/auth.service';

type VerificationState = 'verifying' | 'success' | 'error';

@Component({
  selector: 'app-verify-email',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './verify-email.html',
  styleUrl: './verify-email.scss',
})
export class VerifyEmail {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly state = signal<VerificationState>('verifying');
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly resendSent = signal(false);

  protected readonly resendForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  constructor() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('error');
      this.errorMessage.set('This verification link is missing its token.');
      return;
    }
    this.authService.verifyEmail(token).subscribe({
      next: () => this.state.set('success'),
      error: (err) => {
        this.state.set('error');
        this.errorMessage.set(err?.error?.message ?? 'This verification link is invalid or has expired.');
      },
    });
  }

  resend(): void {
    if (this.resendForm.invalid) {
      this.resendForm.markAllAsTouched();
      return;
    }
    this.authService.resendVerification(this.resendForm.getRawValue().email).subscribe({
      // Backend always returns the same generic response regardless of outcome
      // (brief §8/§46) - the UI reflects that same non-committal framing.
      next: () => this.resendSent.set(true),
      error: () => this.resendSent.set(true),
    });
  }
}
