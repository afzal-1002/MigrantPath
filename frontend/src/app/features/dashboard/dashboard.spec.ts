import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Dashboard as DashboardData } from '../../core/services/dashboard.service';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;
  let router: Router;

  const emptyDashboard: DashboardData = {
    profile: { displayName: 'Test', email: 'user@example.com', accountVerified: true, city: 'Warsaw' },
    primaryCase: null,
    activeCases: [],
    nextActions: [],
    importantDates: [],
    completedMilestones: [],
    latestRecommendations: [],
    recentActivity: [],
    assessmentStatus: null,
  };

  function createComponent(): void {
    fixture = TestBed.createComponent(Dashboard);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  /** Flushes the single dashboard aggregation call the constructor always fires (brief
   * §27's "prefer one endpoint" - see dashboard.service.ts's own doc comment). */
  function flush(data: Partial<DashboardData>): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/dashboard`).flush({ ...emptyDashboard, ...data });
    fixture.detectChanges();
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
    flush({});
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    fixture.componentInstance.logout();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/platform/status`).flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });

  it('renders the full structural shell (hero, timeline, 3x2 grid) for a brand-new user', () => {
    createComponent();
    flush({
      nextActions: [
        {
          heading: 'Find the right pathway for you',
          detail: 'Answer a few questions to see which procedures may be relevant to your situation.',
          severity: 'INFO',
          ctaLabel: 'Start assessment',
          caseId: null,
          assessmentId: null,
        },
      ],
    });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.hero')).toBeTruthy();
    expect(el.querySelector('.timeline')).toBeTruthy();
    expect(el.querySelector('.card-grid')).toBeTruthy();
    expect((el.textContent as string)).toContain('No pathway yet');
    expect((el.textContent as string)).toContain('Find the right pathway for you');
  });

  it("shows the assessment's real progress percentage while one is in progress", () => {
    createComponent();
    flush({
      assessmentStatus: { assessmentId: 'assessment-1', status: 'IN_PROGRESS', progressPercent: 58 },
      nextActions: [
        {
          heading: 'Continue your assessment',
          detail: '58% of the visible questions answered.',
          severity: 'NEXT',
          ctaLabel: 'Continue',
          caseId: null,
          assessmentId: 'assessment-1',
        },
      ],
    });

    expect((fixture.nativeElement.textContent as string)).toContain('58%');
  });

  it('shows the latest recommendation when the assessment is complete and no case exists yet', () => {
    createComponent();
    flush({
      assessmentStatus: { assessmentId: 'assessment-1', status: 'COMPLETED', progressPercent: 100 },
      latestRecommendations: [
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
      nextActions: [
        {
          heading: 'Your recommended pathway is ready',
          detail: 'PESEL number assignment appears relevant based on your answers.',
          severity: 'NEXT',
          ctaLabel: 'View recommendations',
          caseId: null,
          assessmentId: 'assessment-1',
        },
      ],
    });

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('PESEL number assignment');
  });

  it('shows the active case in the hero, timeline and card grid with real checklist progress', () => {
    createComponent();
    flush({
      primaryCase: {
        id: 'case-1',
        procedureCode: 'PESEL',
        procedureTitle: 'PESEL number assignment',
        status: 'PREPARING',
        startedAt: '2026-08-01T00:00:00Z',
        daysActive: 5,
        stepsCompleted: 1,
        stepsTotal: 2,
        documentsCompleted: 1,
        documentsTotal: 1,
        feesCompleted: 0,
        feesTotal: 1,
        hasRequirementUpdates: false,
        stageIndex: 0,
        stageLabel: 'Started',
        authorities: [],
        offices: [],
      },
      activeCases: [
        {
          id: 'case-1',
          procedureCode: 'PESEL',
          procedureTitle: 'PESEL number assignment',
          status: 'PREPARING',
          stepsCompleted: 1,
          stepsTotal: 2,
          documentsReady: 1,
          documentsTotal: 1,
          hasRequirementUpdates: false,
          updatedAt: '2026-08-05T00:00:00Z',
        },
      ],
      nextActions: [
        {
          heading: 'Continue your checklist',
          detail: 'PESEL number assignment: 1/2 steps, 1/1 documents ready.',
          severity: 'NEXT',
          ctaLabel: 'Open checklist',
          caseId: 'case-1',
          assessmentId: null,
        },
      ],
    });

    expect(fixture.componentInstance['caseProgressPercent']()).toBe(67);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('PESEL number assignment');
    expect(text).toContain('Active for 5 days');
  });

  it('flags a requirement update as the top ATTENTION next action', () => {
    createComponent();
    flush({
      primaryCase: {
        id: 'case-1',
        procedureCode: 'PESEL',
        procedureTitle: 'PESEL number assignment',
        status: 'PREPARING',
        startedAt: '2026-08-01T00:00:00Z',
        daysActive: 5,
        stepsCompleted: 2,
        stepsTotal: 4,
        documentsCompleted: 1,
        documentsTotal: 3,
        feesCompleted: 0,
        feesTotal: 1,
        hasRequirementUpdates: true,
        stageIndex: 0,
        stageLabel: 'Started',
        authorities: [],
        offices: [],
      },
      nextActions: [
        {
          heading: 'Requirements have changed',
          detail: 'PESEL number assignment has an updated requirement to review.',
          severity: 'ATTENTION',
          ctaLabel: 'Review changes',
          caseId: 'case-1',
          assessmentId: null,
        },
      ],
    });

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.severity-attention')).toBeTruthy();
    expect((el.textContent as string)).toContain('Requirements have changed');
  });

  it('shows a real, sourced responsible authority when the primary case has one', () => {
    createComponent();
    flush({
      primaryCase: {
        id: 'case-1',
        procedureCode: 'PESEL',
        procedureTitle: 'PESEL number assignment',
        status: 'PREPARING',
        startedAt: '2026-08-01T00:00:00Z',
        daysActive: 1,
        stepsCompleted: 0,
        stepsTotal: 1,
        documentsCompleted: 0,
        documentsTotal: 1,
        feesCompleted: 0,
        feesTotal: 0,
        hasRequirementUpdates: false,
        stageIndex: 0,
        stageLabel: 'Started',
        authorities: [{ code: 'UDSC', name: 'Office for Foreigners', role: 'DECISION_MAKER', officialWebsite: 'https://udsc.gov.pl' }],
        offices: [],
      },
    });

    expect((fixture.nativeElement.textContent as string)).toContain('Office for Foreigners');
  });

  it('shows an error state with a retry action when the aggregation fetch fails', () => {
    createComponent();
    httpMock.expectOne(`${environment.apiBaseUrl}/dashboard`).flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.componentInstance['error']()).toBe(true);
    expect((fixture.nativeElement.textContent as string)).toContain("couldn't load your dashboard");
  });
});
