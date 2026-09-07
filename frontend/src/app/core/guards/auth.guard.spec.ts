import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { authGuard, authMatchGuard } from './auth.guard';

describe('authGuard', () => {
  function runGuard(isAuthenticated: boolean) {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated } },
        { provide: Router, useValue: { parseUrl: (url: string) => ({ url }) as unknown as UrlTree } },
      ],
    });
    return TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
  }

  it('allows navigation when authenticated', () => {
    expect(runGuard(true)).toBe(true);
  });

  it('redirects to /login when not authenticated', () => {
    const result = runGuard(false) as UrlTree & { url: string };
    expect(result.url).toBe('/login');
  });
});

describe('authMatchGuard', () => {
  function runGuard(isAuthenticated: boolean) {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated } }],
    });
    return TestBed.runInInjectionContext(() => authMatchGuard({} as never, [], {} as never));
  }

  it('matches when authenticated - the AppShell-wrapped route wins', () => {
    expect(runGuard(true)).toBe(true);
  });

  it('declines to match (never redirects) when not authenticated, so the router falls through to the public route', () => {
    expect(runGuard(false)).toBe(false);
  });
});
