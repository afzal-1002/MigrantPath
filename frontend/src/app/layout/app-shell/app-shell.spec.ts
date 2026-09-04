import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService, CurrentUser } from '../../core/services/auth.service';
import { AppShell } from './app-shell';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;
  let httpMock: HttpTestingController;
  let router: Router;
  let authService: AuthService;

  const baseUser: CurrentUser = {
    id: 'user-1',
    email: 'user@example.com',
    firstName: 'Test',
    preferredLanguage: null,
    emailVerified: true,
    roles: ['USER'],
  };

  /** Drives `AuthService.currentUser` through its own real `login()` HTTP flow
   * (matching this codebase's established convention - `AuthService.user` is a
   * private signal, never poked at directly from a test). */
  function loginAs(user: CurrentUser): void {
    authService.login(user.email, 'irrelevant').subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(user);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShell);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('renders every primary navigation item', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    for (const label of ['Overview', 'Find my pathway', 'My cases', 'Browse procedures', 'Account', 'Help']) {
      expect(text).toContain(label);
    }
  });

  it('never shows the Administration section for a plain USER account', () => {
    loginAs(baseUser);
    fixture.detectChanges();
    expect((fixture.nativeElement.textContent as string)).not.toContain('Administration');
  });

  it('shows the Administration section for an ADMIN account', () => {
    loginAs({ ...baseUser, roles: ['USER', 'ADMIN'] });
    fixture.detectChanges();
    expect((fixture.nativeElement.textContent as string)).toContain('Administration');
  });

  it("shows the caller's own display name and initial, never another account's", () => {
    loginAs(baseUser);
    fixture.detectChanges();
    expect((fixture.nativeElement.textContent as string)).toContain('Test');
    expect(fixture.componentInstance['userInitials']()).toBe('T');
  });

  it('logout navigates to /login once the backend confirms', () => {
    fixture.detectChanges();
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    fixture.componentInstance.logout();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`).flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });
});
