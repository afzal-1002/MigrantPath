import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

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
