import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService, CurrentUser } from '../../core/services/auth.service';
import { Account } from './account';

describe('Account', () => {
  let fixture: ComponentFixture<Account>;
  let component: Account;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let dialogOpenSpy: ReturnType<typeof vi.fn>;

  const baseUser: CurrentUser = {
    id: 'user-1',
    email: 'user@example.com',
    firstName: 'Test',
    preferredLanguage: null,
    emailVerified: true,
    roles: ['USER'],
  };

  /** Matches every other spec's own convention (AppShell, etc.) - drives
   * `AuthService.currentUser` through its real `login()` HTTP flow rather than poking
   * at the private signal directly. */
  function loginAs(user: CurrentUser): void {
    authService.login(user.email, 'irrelevant').subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(user);
  }

  beforeEach(async () => {
    const dialogRefStub = { afterClosed: () => of(false) } as unknown as MatDialogRef<unknown>;
    await TestBed.configureTestingModule({
      imports: [Account],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Account);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    // Spying on the real, injected MatDialog's own open() method (rather than providing a
    // stand-in object via useValue) - MatDialogModule's own providers, contributed through the
    // component's own `imports`, otherwise win over a TestBed-level useValue override for this
    // service.
    dialogOpenSpy = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue(dialogRefStub) as unknown as ReturnType<typeof vi.fn>;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('exports data by calling the export endpoint and triggering a download', () => {
    const createObjectURLSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    component.export();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/account/export`);
    expect(req.request.method).toBe('GET');
    req.flush(new Blob(['{}'], { type: 'application/json' }));

    expect(component['exporting']()).toBe(false);
    expect(createObjectURLSpy).toHaveBeenCalled();
    expect(revokeSpy).toHaveBeenCalled();
  });

  it('shows an error if export fails', () => {
    component.export();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/account/export`);
    req.flush(new Blob(['error']), { status: 500, statusText: 'Server Error' });

    expect(component['exportError']()).toContain('Could not generate');
  });

  it('opens the delete-account confirmation dialog', () => {
    component.openDeleteDialog();
    expect(dialogOpenSpy).toHaveBeenCalled();
  });

  it('clears local session state and navigates home once the dialog confirms deletion', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    const confirmedRef = { afterClosed: () => of(true) } as unknown as MatDialogRef<unknown>;
    dialogOpenSpy.mockReturnValue(confirmedRef);

    component.openDeleteDialog();

    expect(navigateSpy).toHaveBeenCalledWith('/');
  });

  it('saves the profile form by PATCHing /users/me with the real first name only', () => {
    loginAs(baseUser);
    fixture.detectChanges();

    component['profileForm'].setValue({ firstName: 'Updated Name' });
    component.saveProfile();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/users/me`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ firstName: 'Updated Name' });
    req.flush({ ...baseUser, firstName: 'Updated Name' });

    expect(component['profileSaved']()).toBe(true);
    expect(authService.currentUser()?.firstName).toBe('Updated Name');
  });

  it('shows a server error if saving the profile fails', () => {
    loginAs(baseUser);
    fixture.detectChanges();

    component['profileForm'].setValue({ firstName: 'Updated Name' });
    component.saveProfile();

    httpMock.expectOne(`${environment.apiBaseUrl}/users/me`).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(component['profileError']()).toContain('Could not save');
  });

  it("changes the password, then treats the session as over (the backend invalidates it)", () => {
    loginAs(baseUser);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    component['passwordForm'].setValue({
      currentPassword: 'old-password-123',
      newPassword: 'new-password-456',
      confirmPassword: 'new-password-456',
    });
    component.changePassword();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/users/me/change-password`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ currentPassword: 'old-password-123', newPassword: 'new-password-456' });
    req.flush(null);

    expect(authService.isAuthenticated()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/login'], { queryParams: { passwordChanged: '1' } });
  });

  it('rejects submitting the password form when the confirmation does not match', () => {
    loginAs(baseUser);
    fixture.detectChanges();

    component['passwordForm'].setValue({
      currentPassword: 'old-password-123',
      newPassword: 'new-password-456',
      confirmPassword: 'something-else',
    });

    expect(component['passwordForm'].hasError('passwordMismatch')).toBe(true);
    component.changePassword();
    httpMock.expectNone(`${environment.apiBaseUrl}/users/me/change-password`);
  });

  it('shows a specific error when the current password is wrong', () => {
    loginAs(baseUser);
    fixture.detectChanges();

    component['passwordForm'].setValue({
      currentPassword: 'wrong-password',
      newPassword: 'new-password-456',
      confirmPassword: 'new-password-456',
    });
    component.changePassword();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/users/me/change-password`)
      .flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

    expect(component['passwordError']()).toContain('Current password is incorrect');
  });
});
