import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('does not submit when the form is invalid', () => {
    component.submit();
    httpMock.expectNone(`${environment.apiBaseUrl}/auth/login`);
    expect(component['form'].touched).toBe(true);
  });

  it('navigates to /dashboard on successful login', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    component['form'].setValue({ email: 'person@example.com', password: 'password123' });

    component.submit();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`).flush({
      id: '1',
      email: 'person@example.com',
      firstName: null,
      preferredLanguage: null,
      emailVerified: true,
      roles: ['USER'],
    });

    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');
  });

  it('shows the backend error message on failed login', () => {
    component['form'].setValue({ email: 'person@example.com', password: 'wrong-password' });

    component.submit();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/auth/login`)
      .flush(
        { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(component['serverError']()).toBe('Invalid email or password');
  });
});
