import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminUser, AdminUserService } from '../../../../core/services/admin/admin-user.service';

const ASSIGNABLE_ROLES = ['CONTENT_EDITOR', 'LEGAL_REVIEWER', 'ADMIN'];

/**
 * Route: /admin/users - ADMIN-only (brief §81-§83). Search by email, view roles, assign/remove an
 * administrative role - deliberately nothing else (no Assessments/UserCases/private data).
 */
@Component({
  selector: 'app-user-admin',
  imports: [FormsModule],
  templateUrl: './user-admin.html',
})
export class UserAdmin {
  private readonly userService = inject(AdminUserService);

  protected readonly assignableRoles = ASSIGNABLE_ROLES;
  protected readonly query = signal('');
  protected readonly results = signal<AdminUser[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly roleToAssign = signal<Record<string, string>>({});

  protected search(): void {
    if (!this.query().trim()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.userService.search(this.query()).subscribe({
      next: (users) => {
        this.results.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not search users.');
        this.loading.set(false);
      },
    });
  }

  protected setRoleToAssign(userId: string, role: string): void {
    this.roleToAssign.update((m) => ({ ...m, [userId]: role }));
  }

  protected assign(userId: string): void {
    const role = this.roleToAssign()[userId] ?? ASSIGNABLE_ROLES[0];
    this.error.set(null);
    this.userService.assignRole(userId, role).subscribe({
      next: (updated) => this.replace(updated),
      error: (err) => this.error.set(err?.error?.message ?? 'Could not assign role'),
    });
  }

  protected remove(userId: string, role: string): void {
    this.error.set(null);
    this.userService.removeRole(userId, role).subscribe({
      next: (updated) => this.replace(updated),
      error: (err) => this.error.set(err?.error?.message ?? 'Could not remove role'),
    });
  }

  private replace(updated: AdminUser): void {
    this.results.update((users) => users.map((u) => (u.id === updated.id ? updated : u)));
  }
}
