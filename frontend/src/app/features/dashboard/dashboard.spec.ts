import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AssessmentDetail, AssessmentSummary } from '../../core/services/assessment.service';
import { CaseSummary } from '../../core/services/case.service';
import { RecommendationRun } from '../../core/services/recommendation.service';
import { ProcedureSummary } from '../../core/services/procedure.service';
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

  /** Flushes the two calls the constructor always fires (brief §30 - bounded fetch). */
  function flushPrimary(assessments: AssessmentSummary[], cases: CaseSummary[]): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/assessments`).flush(assessments);
    httpMock.expectOne(`${environment.apiBaseUrl}/cases`).flush(cases);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  // `ignoreCancelled: true` - real RxJS semantics found while writing the error-state
  // test below: forkJoin unsubscribes from (cancels) its sibling request the instant
  // one source errors, and Angular's HttpTestingController still counts a cancelled-
  // but-unflushed request as "open" unless told otherwise. Every other test in this
  // file flushes every request it triggers as normal, so this stays exactly as strict
  // for the failure mode that actually matters (a genuinely forgotten request).
  afterEach(() => httpMock.verify({ ignoreCancelled: true }));

  it('logout navigates to /login once the backend confirms', () => {
    createComponent();
    flushPrimary([], []);
    // A brand-new user (no assessments, no cases) also triggers the "popular
    // procedures" follow-up fetch - must be flushed too, or the pending request
    // trips httpMock.verify() in afterEach and corrupts every later test's TestBed.
    httpMock.expectOne(`${environment.apiBaseUrl}/procedures`).flush([]);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    fixture.componentInstance.logout();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`).flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });

  it('shows onboarding and offers to start an assessment for a brand-new user', () => {
    createComponent();
    flushPrimary([], []);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/procedures`)
      .flush([{ code: 'PESEL', name: 'PESEL number', category: 'ADMIN', summary: null, jurisdictionScope: 'MUNICIPAL' }] as ProcedureSummary[]);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Start assessment');
    expect(text).toContain('How it works');
  });

  it('offers to resume an in-progress assessment with its real progress percentage', () => {
    createComponent();
    flushPrimary(
      [
        {
          id: 'in-progress-1',
          status: 'IN_PROGRESS',
          questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
          startedAt: '2026-02-01T00:00:00Z',
          completedAt: null,
        },
      ],
      [],
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/assessments/in-progress-1`).flush({
      progressPercent: 58,
    } as AssessmentDetail);
    fixture.detectChanges();

    expect(fixture.componentInstance['assessmentProgressPercent']()).toBe(58);
    expect((fixture.nativeElement.textContent as string)).toContain('58% of the visible questions answered');
  });

  it('shows the latest recommendation when the assessment is complete and no case exists yet', () => {
    createComponent();
    flushPrimary(
      [
        {
          id: 'completed-1',
          status: 'COMPLETED',
          questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
          startedAt: '2026-01-01T00:00:00Z',
          completedAt: '2026-01-02T00:00:00Z',
        },
      ],
      [],
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/assessments/completed-1/recommendations/latest`).flush({
      id: 'run-1',
      assessmentId: 'completed-1',
      evaluationDate: '2026-01-02',
      status: 'COMPLETED',
      recommendationEngineVersion: '1',
      ruleEngineVersion: '1',
      createdAt: '2026-01-02T00:00:00Z',
      completedAt: '2026-01-02T00:00:00Z',
      recommendations: [
        {
          id: 'rec-1',
          procedureCode: 'PESEL',
          procedureTitle: 'PESEL number assignment',
          recommendationType: 'PRIMARY_MATCH',
          rank: 1,
          reasons: [],
          missingFacts: [],
          officialSources: [],
        },
      ],
    } as RecommendationRun);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('PESEL number assignment');
    expect(text).toContain('appears relevant based on your answers');
  });

  it('prioritizes an active case with changed requirements over everything else', () => {
    createComponent();
    flushPrimary(
      [
        {
          id: 'completed-1',
          status: 'COMPLETED',
          questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
          startedAt: '2026-01-01T00:00:00Z',
          completedAt: '2026-01-02T00:00:00Z',
        },
      ],
      [
        {
          id: 'case-1',
          procedureCode: 'PESEL',
          procedureTitle: 'PESEL number assignment',
          status: 'PREPARING',
          stepsCompleted: 2,
          stepsTotal: 4,
          documentsReady: 1,
          documentsTotal: 3,
          hasRequirementUpdates: true,
          updatedAt: '2026-02-01T00:00:00Z',
        },
      ],
    );
    fixture.detectChanges();

    const action = fixture.componentInstance['nextAction']();
    expect(action?.ctaLabel).toBe('Review changes');
    expect((fixture.nativeElement.textContent as string)).toContain('Requirements have changed');
  });

  it('shows an active case with no attention flag as a normal checklist next action', () => {
    createComponent();
    flushPrimary(
      [],
      [
        {
          id: 'case-1',
          procedureCode: 'MELDUNEK',
          procedureTitle: 'Address registration',
          status: 'PREPARING',
          stepsCompleted: 1,
          stepsTotal: 3,
          documentsReady: 0,
          documentsTotal: 2,
          hasRequirementUpdates: false,
          updatedAt: '2026-02-01T00:00:00Z',
        },
      ],
    );
    fixture.detectChanges();

    const action = fixture.componentInstance['nextAction']();
    expect(action?.ctaLabel).toBe('Open checklist');
    expect(fixture.componentInstance['checklistProgressPercent']()).toBe(33);
  });

  it('shows an error state with a retry action when the primary fetch fails', () => {
    createComponent();
    // forkJoin unsubscribes from its sibling the instant one source errors - the
    // /cases request is cancelled the moment /assessments errors below, so there is
    // nothing left to flush for it (a real RxJS semantic found while writing this
    // test, not a test bug to work around by flushing both).
    httpMock.expectOne(`${environment.apiBaseUrl}/assessments`).flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.componentInstance['error']()).toBe(true);
    expect((fixture.nativeElement.textContent as string)).toContain("couldn't load your dashboard");
  });
});
