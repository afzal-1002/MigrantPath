import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { CaseDetail } from '../../../core/services/case.service';
import { CaseDetailPage } from './case-detail';

const BASE = `${environment.apiBaseUrl}/cases/c1`;

function detail(overrides: Partial<CaseDetail> = {}): CaseDetail {
  return {
    id: 'c1',
    procedureCode: 'TEST_PROCEDURE',
    procedureTitle: 'Test Procedure',
    status: 'PREPARING',
    createdAt: '2026-09-03T00:00:00Z',
    updatedAt: '2026-09-03T00:00:00Z',
    submittedAt: null,
    completedAt: null,
    evaluationDate: '2026-09-03',
    revisionNumber: 1,
    progress: { stepsCompleted: 0, stepsTotal: 1, documentsReady: 0, documentsTotal: 1, conditionalDocumentsToReview: 0 },
    steps: [
      {
        id: 's1',
        stableCode: 'STEP_1',
        title: 'Prepare documents',
        description: null,
        detailedInstructions: null,
        stepType: 'PREPARATION',
        sortOrder: 1,
        mandatory: true,
        status: 'NOT_STARTED',
        completedAt: null,
      },
    ],
    documents: [
      {
        id: 'd1',
        stableCode: 'DOC_1',
        name: 'Passport',
        description: null,
        requirementType: 'DEFAULT_REQUIRED',
        applicability: 'APPLICABLE',
        mandatory: true,
        numberOfCopies: null,
        originalRequired: null,
        translationRequired: null,
        swornTranslationRequired: null,
        apostilleRequired: null,
        legalisationRequired: null,
        validityPeriodDescription: null,
        contentNotes: null,
        userNote: null,
        sortOrder: 1,
        status: 'NOT_STARTED',
        readyAt: null,
      },
    ],
    fees: [],
    authorities: [],
    offices: [],
    sources: [],
    hasRequirementUpdates: false,
    ...overrides,
  };
}

async function setUp() {
  await TestBed.configureTestingModule({
    imports: [CaseDetailPage],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'c1' }) } } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(CaseDetailPage);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, httpMock };
}

describe('CaseDetailPage', () => {
  it('renders the checklist once loaded', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush(detail());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Prepare documents');
    expect(fixture.nativeElement.textContent).toContain('Passport');
    httpMock.verify();
  });

  it('marking a step complete PATCHes the backend and reflects the new detail', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();
    httpMock.expectOne(BASE).flush(detail());
    fixture.detectChanges();

    fixture.componentInstance['setStepStatus'](detail().steps[0], 'COMPLETED');
    const req = httpMock.expectOne(`${BASE}/steps/s1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'COMPLETED' });
    req.flush(detail({ steps: [{ ...detail().steps[0], status: 'COMPLETED' }], progress: { ...detail().progress, stepsCompleted: 1 } }));
    fixture.detectChanges();

    expect(fixture.componentInstance['detail']()?.progress.stepsCompleted).toBe(1);
    httpMock.verify();
  });

  it('fetches requirement changes automatically when the case has updates, and shows the banner', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();
    httpMock.expectOne(BASE).flush(detail({ hasRequirementUpdates: true }));
    fixture.detectChanges();

    httpMock.expectOne(`${BASE}/requirement-changes`).flush({
      newerVersionAvailable: true,
      changes: [{ changeType: 'CHANGED', category: 'STEP', stableCode: 'STEP_1', title: 'Prepare documents', detail: 'Step content changed' }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Requirements have changed');
    httpMock.verify();
  });

  it('shows a not-found state for an unknown case id', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush({ code: 'CASE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.componentInstance['notFound']()).toBe(true);
    httpMock.verify();
  });
});
