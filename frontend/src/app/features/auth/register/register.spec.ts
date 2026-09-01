import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Register } from './register';

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let component: Register;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('requires both consent checkboxes to be checked', () => {
    component['form'].patchValue({
      email: 'person@example.com',
      password: 'password12345',
      acceptTerms: false,
      acceptPrivacyPolicy: true,
    });
    expect(component['form'].valid).toBe(false);
  });

  it('rejects a password shorter than 10 characters', () => {
    component['form'].patchValue({ password: 'short' });
    expect(component['form'].controls.password.hasError('minlength')).toBe(true);
  });

  it('does not submit when the form is invalid', () => {
    component.submit();
    httpMock.expectNone(`${environment.apiBaseUrl}/auth/register`);
  });

  it('shows the "check your email" state after a successful registration', () => {
    component['form'].setValue({
      email: 'person@example.com',
      password: 'password12345',
      firstName: 'Pat',
      acceptTerms: true,
      acceptPrivacyPolicy: true,
    });

    component.submit();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/register`).flush({
      id: '1',
      email: 'person@example.com',
      firstName: 'Pat',
      preferredLanguage: null,
      emailVerified: false,
      roles: ['USER'],
    });

    expect(component['registered']()).toBe(true);
  });

  it('shows a conflict error for an already-registered email', () => {
    component['form'].setValue({
      email: 'person@example.com',
      password: 'password12345',
      firstName: '',
      acceptTerms: true,
      acceptPrivacyPolicy: true,
    });

    component.submit();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/auth/register`)
      .flush(
        { code: 'EMAIL_ALREADY_REGISTERED', message: 'An account with this email already exists' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(component['serverError']()).toBe('An account with this email already exists');
    expect(component['registered']()).toBe(false);
  });
});
