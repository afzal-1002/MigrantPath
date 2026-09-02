import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Home } from './home';

describe('Home', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows the connected state once the platform-status call succeeds', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`);
    req.flush({ status: 'UP', application: 'Foreigner Warsaw', version: '0.0.1-SNAPSHOT' });

    expect(fixture.componentInstance['connectionState']()).toBe('connected');
  });

  it('shows the unreachable state if the platform-status call fails', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`);
    req.error(new ProgressEvent('network error'));

    expect(fixture.componentInstance['connectionState']()).toBe('unreachable');
  });

  it('offers both the questionnaire and the browse-procedures entry points (brief §2/§5)', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`).flush({
      status: 'UP',
      application: 'Foreigner Warsaw',
      version: '0.0.1-SNAPSHOT',
    });

    const links = fixture.nativeElement.querySelectorAll('a[href]');
    const hrefs = Array.from(links as NodeListOf<HTMLAnchorElement>).map((a) => a.getAttribute('href'));
    expect(hrefs).toContain('/assessment/start');
    expect(hrefs).toContain('/procedures');
  });
});
