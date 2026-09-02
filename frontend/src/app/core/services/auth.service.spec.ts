import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthService, CurrentUser } from './auth.service';

const SAMPLE_USER: CurrentUser = {
  id: 'user-1',
  email: 'person@example.com',
  firstName: 'Pat',
  preferredLanguage: null,
  emailVerified: true,
  roles: ['USER'],
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts in the UNKNOWN state before loadCurrentUser resolves', () => {
    expect(service.authState()).toBe('UNKNOWN');
    expect(service.isAuthenticated()).toBe(false);
  });

  it('loadCurrentUser sets AUTHENTICATED and stores the user on success', () => {
    service.loadCurrentUser().subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/users/me`).flush(SAMPLE_USER);

    expect(service.authState()).toBe('AUTHENTICATED');
    expect(service.currentUser()).toEqual(SAMPLE_USER);
  });

  it('loadCurrentUser sets UNAUTHENTICATED on a 401, not an error', () => {
    let completed = false;
    service.loadCurrentUser().subscribe({
      next: (user) => expect(user).toBeNull(),
      complete: () => (completed = true),
    });
    httpMock.expectOne(`${environment.apiBaseUrl}/users/me`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(service.authState()).toBe('UNAUTHENTICATED');
    expect(service.currentUser()).toBeNull();
    expect(completed).toBe(true);
  });

  it('login stores the user and sets AUTHENTICATED', () => {
    service.login('person@example.com', 'password123').subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(SAMPLE_USER);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.currentUser()?.email).toBe('person@example.com');
  });

  it('logout clears the user and sets UNAUTHENTICATED', () => {
    service.login('person@example.com', 'password123').subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(SAMPLE_USER);

    service.logout().subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`).flush(null);
    // Re-primes the XSRF-TOKEN cookie Spring Security's logout handler clears - see
    // AuthService#logout's Javadoc.
    httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`).flush({});

    expect(service.isAuthenticated()).toBe(false);
    expect(service.currentUser()).toBeNull();
  });

  it('does not store any auth token/session value - only the non-sensitive user summary', () => {
    service.login('person@example.com', 'password123').subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush(SAMPLE_USER);

    expect(Object.keys(service.currentUser() ?? {})).toEqual(
      expect.arrayContaining(['id', 'email', 'firstName', 'preferredLanguage', 'emailVerified', 'roles']),
    );
  });
});
