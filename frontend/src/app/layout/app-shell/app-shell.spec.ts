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

  /** The shell's own constructor fires one dashboard summary fetch (brief §27's "reuse
   * one endpoint" - see app-shell.ts's own doc comment) - every test must account for
   * it or `httpMock.verify()` below fails on an unflushed request. */
  function flushDashboard(body: Partial<Record<string, unknown>> = {}): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/dashboard`).flush({
      profile: { displayName: 'Test', email: 'user@example.com', accountVerified: true, city: 'Warsaw' },
      primaryCase: null,
      activeCases: [],
      nextActions: [],
      importantDates: [],
      completedMilestones: [],
      latestRecommendations: [],
      recentActivity: [],
      assessmentStatus: null,
      ...body,
    });
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

  it('renders every primary navigation item as an icon link with its label available (hover tooltip text)', () => {
    fixture.detectChanges();
    flushDashboard();
    fixture.detectChanges();

    const rail: HTMLElement = fixture.nativeElement.querySelector('.rail');
    const text = rail.textContent as string;
    for (const label of ['Dashboard', 'Find my pathway', 'My Cases', 'Procedures', 'Help', 'Account', 'Recommendations']) {
      expect(text, label).toContain(label);
    }
  });

  it('never shows the Administration icon for a plain USER account', () => {
    loginAs(baseUser);
    fixture.detectChanges();
    flushDashboard();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('a[href="/admin"]')).toBeNull();
  });

  it('shows the Administration icon for an ADMIN account', () => {
    loginAs({ ...baseUser, roles: ['USER', 'ADMIN'] });
    fixture.detectChanges();
    flushDashboard();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('a[href="/admin"]')).toBeTruthy();
  });

  it("shows the caller's own display name and initial, never another account's", () => {
    loginAs(baseUser);
    fixture.detectChanges();
    flushDashboard();
    fixture.detectChanges();

    expect((fixture.nativeElement.textContent as string)).toContain('Test');
    expect(fixture.componentInstance['userInitials']()).toBe('T');
  });

  it('shows a bell badge only when the dashboard reports a real ATTENTION next action', () => {
    fixture.detectChanges();
    flushDashboard({
      nextActions: [
        { heading: 'Requirements have changed', detail: 'Review it', severity: 'ATTENTION', ctaLabel: 'Review', caseId: 'case-1', assessmentId: null },
        { heading: 'Continue your checklist', detail: 'Keep going', severity: 'NEXT', ctaLabel: 'Open', caseId: 'case-1', assessmentId: null },
      ],
    });
    fixture.detectChanges();

    const badge: HTMLElement | null = fixture.nativeElement.querySelector('.bell-badge');
    expect(badge?.textContent?.trim()).toBe('1');
  });

  it("links the Recommendations item to the caller's latest completed assessment when one exists", () => {
    fixture.detectChanges();
    flushDashboard({ assessmentStatus: { assessmentId: 'assessment-9', status: 'COMPLETED', progressPercent: 100 } });
    fixture.detectChanges();

    const links: HTMLAnchorElement[] = Array.from(fixture.nativeElement.querySelectorAll('.rail a'));
    const recommendationsLink = links.find((a) => a.textContent?.includes('Recommendations'));
    expect(recommendationsLink?.getAttribute('href')).toBe('/assessment/assessment-9/results');
  });

  it('never blocks rendering if the dashboard summary fetch fails', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/dashboard`).error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.rail')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.bell-badge')).toBeNull();
  });

  it('logout navigates to /login once the backend confirms', () => {
    fixture.detectChanges();
    flushDashboard();
    fixture.detectChanges();
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    fixture.componentInstance.logout();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`).flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });
});
