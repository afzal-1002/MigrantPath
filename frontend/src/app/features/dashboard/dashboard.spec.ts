import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AssessmentSummary } from '../../core/services/assessment.service';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;
  let router: Router;

  function createComponent(): void {
    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  function flushAssessments(assessments: AssessmentSummary[]): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/assessments`).flush(assessments);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => httpMock.verify());

  it('logout navigates to /login once the backend confirms', () => {
    createComponent();
    flushAssessments([]);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    fixture.componentInstance.logout();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`).flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });

  it('offers to start an assessment when the caller has none yet', () => {
    createComponent();
    flushAssessments([]);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Help me choose');
  });

  it('offers to resume an in-progress assessment over a completed one', () => {
    createComponent();
    flushAssessments([
      {
        id: 'completed-1',
        status: 'COMPLETED',
        questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
        startedAt: '2026-01-01T00:00:00Z',
        completedAt: '2026-01-02T00:00:00Z',
      },
      {
        id: 'in-progress-1',
        status: 'IN_PROGRESS',
        questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
        startedAt: '2026-02-01T00:00:00Z',
        completedAt: null,
      },
    ]);
    fixture.detectChanges();

    expect(fixture.componentInstance['relevantAssessment']()?.id).toBe('in-progress-1');
    expect((fixture.nativeElement.textContent as string)).toContain('Resume assessment');
  });

  it('offers to view a completed assessment when nothing is in progress', () => {
    createComponent();
    flushAssessments([
      {
        id: 'completed-1',
        status: 'COMPLETED',
        questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
        startedAt: '2026-01-01T00:00:00Z',
        completedAt: '2026-01-02T00:00:00Z',
      },
    ]);
    fixture.detectChanges();

    expect((fixture.nativeElement.textContent as string)).toContain('Assessment completed');
  });
});
