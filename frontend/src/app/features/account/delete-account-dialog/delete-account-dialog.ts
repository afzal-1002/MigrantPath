import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AccountService } from '../../../core/services/account.service';

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) brief §28/§29/§53/§194 - a real reauthentication
 * (current password) plus an explicit typed confirmation ("DELETE"), never a bare checkbox, and
 * plainly states what's removed before the destructive action is possible. Keyboard-accessible by
 * construction (Angular Material's dialog manages its own focus trap; the form's own tab order is
 * the only thing this component needs to get right).
 */
@Component({
  selector: 'app-delete-account-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './delete-account-dialog.html',
  styleUrl: './delete-account-dialog.scss',
})
export class DeleteAccountDialog {
  private readonly accountService = inject(AccountService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<DeleteAccountDialog>);

  protected readonly submitting = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    currentPassword: ['', Validators.required],
    confirmation: ['', [Validators.required, this.mustEqualDelete]],
  });

  private mustEqualDelete(control: { value: string }) {
    return control.value === 'DELETE' ? null : { mustEqualDelete: true };
  }

  cancel(): void {
    this.dialogRef.close(false);
  }

  confirmDelete(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.serverError.set(null);
    const { currentPassword } = this.form.getRawValue();
    this.accountService.deleteAccount(currentPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.submitting.set(false);
        this.serverError.set(
          err?.error?.message ?? 'Could not delete your account. Please try again.',
        );
      },
    });
  }
}
