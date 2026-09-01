import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
  function runGuard(isAuthenticated: boolean) {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated } },
        { provide: Router, useValue: { parseUrl: (url: string) => ({ url }) as unknown as UrlTree } },
      ],
    });
    return TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
  }

  it('allows navigation when not authenticated', () => {
    expect(runGuard(false)).toBe(true);
  });

  it('redirects to /dashboard when already authenticated', () => {
    const result = runGuard(true) as UrlTree & { url: string };
    expect(result.url).toBe('/dashboard');
  });
});
