import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { RecommendationRun } from '../../../core/services/recommendation.service';
import { RecommendationResults } from './recommendation-results';

const BASE = `${environment.apiBaseUrl}/assessments/a1`;

function sampleRun(overrides: Partial<RecommendationRun> = {}): RecommendationRun {
  return {
    id: 'run-1',
    assessmentId: 'a1',
    evaluationDate: '2026-09-03',
    status: 'COMPLETED',
    recommendationEngineVersion: '1',
    ruleEngineVersion: '1',
    createdAt: '2026-09-03T00:00:00Z',
    completedAt: '2026-09-03T00:00:01Z',
    recommendations: [
      {
        id: 'rec-1',
        procedureCode: 'TEST_MATCH',
        procedureTitle: 'Test Match Procedure',
        recommendationType: 'PRIMARY_MATCH',
        rank: 1,
        reasons: [{ reasonType: 'MATCHED_CONDITION', reasonCode: 'GOAL_MATCH', messageKey: null, factCode: null }],
        missingFacts: [],
        officialSources: [],
      },
    ],
    ...overrides,
  };
}

async function setUp() {
  await TestBed.configureTestingModule({
    imports: [RecommendationResults],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'a1' }) } } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(RecommendationResults);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, httpMock };
}

describe('RecommendationResults', () => {
  it('renders the latest run grouped by category', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/recommendations/latest`).flush(sampleRun());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Test Match Procedure');
    expect(fixture.nativeElement.textContent).toContain('Most relevant');
    httpMock.verify();
  });

  it('auto-analyzes when no run exists yet (404 RECOMMENDATION_RUN_NOT_FOUND)', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock
      .expectOne(`${BASE}/recommendations/latest`)
      .flush({ code: 'RECOMMENDATION_RUN_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/recommendation-runs`).flush(sampleRun());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Test Match Procedure');
    httpMock.verify();
  });

  it('shows a not-completed notice and never an analysis, on 409 ASSESSMENT_NOT_COMPLETED', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock
      .expectOne(`${BASE}/recommendations/latest`)
      .flush({ code: 'RECOMMENDATION_RUN_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    httpMock
      .expectOne(`${BASE}/recommendation-runs`)
      .flush({ code: 'ASSESSMENT_NOT_COMPLETED' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("isn't complete yet");
    httpMock.verify();
  });

  it('shows a warning banner, never an error, for a PARTIAL run', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/recommendations/latest`).flush(sampleRun({ status: 'PARTIAL' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('could not be fully analysed');
    httpMock.verify();
  });

  it('shows the empty-result message when nothing matches, never a fabricated fallback', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/recommendations/latest`).flush(sampleRun({ recommendations: [] }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("couldn't identify a matching pathway");
    httpMock.verify();
  });

  it('never renders a numeric confidence anywhere', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/recommendations/latest`).flush(sampleRun());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toMatch(/\d+%/);
    httpMock.verify();
  });

  it('"Run analysis again" creates a new run via POST', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/recommendations/latest`).flush(sampleRun());
    fixture.detectChanges();

    fixture.componentInstance['analyze']();
    httpMock.expectOne(`${BASE}/recommendation-runs`).flush(sampleRun({ id: 'run-2' }));
    fixture.detectChanges();

    expect(fixture.componentInstance['run']()?.id).toBe('run-2');
    httpMock.verify();
  });

  it('shows "Start this pathway" for a PRIMARY_MATCH but not for MORE_INFORMATION_REQUIRED', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/recommendations/latest`).flush(
      sampleRun({
        recommendations: [
          { ...sampleRun().recommendations[0] },
          {
            id: 'rec-2',
            procedureCode: 'TEST_MORE_INFO',
            procedureTitle: 'Test More Info Procedure',
            recommendationType: 'MORE_INFORMATION_REQUIRED',
            rank: 2,
            reasons: [],
            missingFacts: ['MONTHLY_GROSS_SALARY'],
            officialSources: [],
          },
        ],
      }),
    );
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const startButtons = buttons.filter((b) => b.textContent?.includes('Start this pathway'));
    expect(startButtons.length).toBe(1);
    httpMock.verify();
  });

  it('clicking "Start this pathway" creates a case and navigates to it', async () => {
    const { fixture, httpMock } = await setUp();
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');
    fixture.detectChanges();
    httpMock.expectOne(`${BASE}/recommendations/latest`).flush(sampleRun());
    fixture.detectChanges();

    fixture.componentInstance['startPathway'](sampleRun().recommendations[0]);
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/recommendations/rec-1/cases`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'case-1' });

    expect(navigateSpy).toHaveBeenCalledWith(['/cases', 'case-1']);
    httpMock.verify();
  });
});
