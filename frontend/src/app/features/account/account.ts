import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { AccountService } from '../../core/services/account.service';
import { AuthService } from '../../core/services/auth.service';
import { DeleteAccountDialog } from './delete-account-dialog/delete-account-dialog';

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) self-service privacy page (brief §26/§51-§53) - a
 * single, minimal page rather than a larger settings system, matching the brief's own "do not
 * build a large settings system" instruction.
 *
 * <p>Deliberately does not import {@code MatDialogModule} - this component's own template uses
 * no dialog directives, and {@link MatDialog} is {@code providedIn: 'root'} so it injects fine
 * without it. Importing it anyway (as an earlier version of this file did) gives the component
 * its own module-scoped {@code MatDialog} provider instance, distinct from the root singleton -
 * harmless at runtime but it silently defeats a test's `TestBed.inject(MatDialog)` spy, which is
 * how this was actually found.
 */
@Component({
  selector: 'app-account',
  imports: [MatCardModule, MatButtonModule],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  private readonly accountService = inject(AccountService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  protected readonly currentUser = this.authService.currentUser;
  protected readonly exporting = signal(false);
  protected readonly exportError = signal<string | null>(null);

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
