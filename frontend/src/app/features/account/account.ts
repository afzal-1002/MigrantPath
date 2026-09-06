import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AccountService } from '../../core/services/account.service';
import { AuthService } from '../../core/services/auth.service';
import { DeleteAccountDialog } from './delete-account-dialog/delete-account-dialog';
import { Icon } from '../../shared/icon/icon';

const MIN_PASSWORD_LENGTH = 10;

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const password = control.get('newPassword')?.value;
  const confirm = control.get('confirmPassword')?.value;
  return password === confirm ? null : { passwordMismatch: true };
}

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) self-service privacy page (brief §26/§51-§53).
 *
 * UI redesign ("Eviza" reference, visual style only - same "no fabricated fields, no fake
 * controls" discipline as the last two passes): this page now wires two backend endpoints that
 * already existed but nothing in the frontend had ever called - `PATCH /api/v1/users/me`
 * (first name only; Phase 2's own scope note says preferred language/citizenship/residence
 * fields arrive in a later phase) and `POST /api/v1/users/me/change-password` (already used by
 * {@link AuthService.changePassword}, just never wired to a UI). No "Last Name," "Phone
 * Number," or photo-upload widget - none of those exist on {@code User} at all, and adding them
 * would mean a backend/data-model change this pass is explicitly scoped not to make; showing
 * input fields or an "Update/Delete avatar" control with nothing behind them would be exactly
 * the fake, non-functional UI this codebase has consistently avoided.
 *
 * <p>Deliberately does not import {@code MatDialogModule} - see this class's own prior history:
 * importing it gives the component its own module-scoped {@code MatDialog} provider instance,
 * distinct from the root singleton - harmless at runtime but it silently defeats a test's
 * {@code TestBed.inject(MatDialog)} spy.
 */
@Component({
  selector: 'app-account',
  imports: [ReactiveFormsModule, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule, Icon],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  private readonly accountService = inject(AccountService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly currentUser = this.authService.currentUser;
  protected readonly exporting = signal(false);
  protected readonly exportError = signal<string | null>(null);

  protected readonly profileForm = this.formBuilder.nonNullable.group({
    firstName: [this.currentUser()?.firstName ?? '', Validators.maxLength(100)],
  });
  protected readonly savingProfile = signal(false);
  protected readonly profileSaved = signal(false);
  protected readonly profileError = signal<string | null>(null);

  protected readonly passwordForm = this.formBuilder.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );
  protected readonly changingPassword = signal(false);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly showCurrentPassword = signal(false);
  protected readonly showNewPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.savingProfile.set(true);
    this.profileSaved.set(false);
    this.profileError.set(null);
    this.authService.updateProfile(this.profileForm.getRawValue().firstName).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.profileSaved.set(true);
      },
      error: () => {
        this.savingProfile.set(false);
        this.profileError.set('Could not save your changes. Please try again.');
      },
    });
  }

  resetProfileForm(): void {
    this.profileForm.reset({ firstName: this.currentUser()?.firstName ?? '' });
    this.profileSaved.set(false);
    this.profileError.set(null);
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.changingPassword.set(true);
    this.passwordError.set(null);
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.authService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        // The backend invalidates every session for this account, including the one
        // that just made this request (UserAccountService.changePassword's own real
        // behavior) - so the frontend must treat this session as over immediately,
        // the same way account deletion already does, rather than wait for the next
        // request to discover it with a 401.
        this.authService.clearSessionLocally();
        this.router.navigate(['/login'], { queryParams: { passwordChanged: '1' } });
      },
      error: (err) => {
        this.changingPassword.set(false);
        this.passwordError.set(
          err?.status === 401 ? 'Current password is incorrect.' : 'Could not change your password. Please try again.',
        );
      },
    });
  }

  export(): void {
    this.exporting.set(true);
    this.exportError.set(null);
    this.accountService.exportData().subscribe({
      next: (blob) => {
        this.exporting.set(false);
        this.triggerDownload(blob);
      },
      error: () => {
        this.exporting.set(false);
        this.exportError.set('Could not generate your export. Please try again.');
      },
    });
  }

  openDeleteDialog(): void {
    const ref = this.dialog.open(DeleteAccountDialog);
    ref.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed) {
        this.authService.clearSessionLocally();
        this.router.navigateByUrl('/');
      }
    });
  }

  private triggerDownload(blob: Blob): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'account-export.json';
    link.click();
    URL.revokeObjectURL(url);
  }
}
