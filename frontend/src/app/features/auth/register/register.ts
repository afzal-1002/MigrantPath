import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../../../core/services/auth.service';

/** Password minlength (10) mirrors the backend's policy exactly (brief §6/§31 - "the
 * backend must repeat all critical validation," true here in the other direction too:
 * this frontend rule exists for early feedback, not as the source of truth). */
const MIN_PASSWORD_LENGTH = 10;

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
  ],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly registered = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
    firstName: [''],
    acceptTerms: [false, Validators.requiredTrue],
    acceptPrivacyPolicy: [false, Validators.requiredTrue],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.serverError.set(null);
    const { email, password, firstName, acceptTerms, acceptPrivacyPolicy } = this.form.getRawValue();
    this.authService.register({ email, password, firstName: firstName || undefined, acceptTerms, acceptPrivacyPolicy }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.registered.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        this.serverError.set(err?.error?.message ?? 'Registration failed. Please try again.');
      },
    });
  }
}
