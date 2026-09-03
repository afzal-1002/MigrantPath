import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  function runGuard(roles: string[]) {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { currentUser: () => ({ roles }) } },
        { provide: Router, useValue: { parseUrl: (url: string) => ({ url }) as unknown as UrlTree } },
      ],
    });
    return TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
  }

  it('allows navigation for CONTENT_EDITOR', () => {
    expect(runGuard(['CONTENT_EDITOR'])).toBe(true);
  });

  it('allows navigation for LEGAL_REVIEWER', () => {
    expect(runGuard(['LEGAL_REVIEWER'])).toBe(true);
  });

  it('allows navigation for ADMIN', () => {
    expect(runGuard(['ADMIN'])).toBe(true);
  });

  it('redirects a plain USER to /dashboard', () => {
    const result = runGuard(['USER']) as UrlTree & { url: string };
    expect(result.url).toBe('/dashboard');
  });

  it('redirects an unauthenticated visitor (no roles) to /dashboard', () => {
    const result = runGuard([]) as UrlTree & { url: string };
    expect(result.url).toBe('/dashboard');
  });
});
