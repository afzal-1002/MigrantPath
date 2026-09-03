import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { CaseSummary } from '../../../core/services/case.service';
import { CaseList } from './case-list';

const BASE = `${environment.apiBaseUrl}/cases`;

function summary(overrides: Partial<CaseSummary> = {}): CaseSummary {
  return {
    id: 'case-1',
    procedureCode: 'TEST_PROCEDURE',
    procedureTitle: 'Test Procedure',
    status: 'PREPARING',
    stepsCompleted: 1,
    stepsTotal: 3,
    documentsReady: 2,
    documentsTotal: 4,
    hasRequirementUpdates: false,
    updatedAt: '2026-09-03T00:00:00Z',
    ...overrides,
  };
}

async function setUp() {
  await TestBed.configureTestingModule({
    imports: [CaseList],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  }).compileComponents();
  const fixture = TestBed.createComponent(CaseList);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, httpMock };
}

describe('CaseList', () => {
  it('shows the empty state when the caller has no cases', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("haven't started tracking");
    httpMock.verify();
  });

  it('groups active cases separately from completed/cancelled ones', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock
      .expectOne(BASE)
      .flush([summary({ status: 'PREPARING' }), summary({ id: 'case-2', status: 'COMPLETED' })]);
    fixture.detectChanges();

    expect(fixture.componentInstance['activeCases']().length).toBe(1);
    expect(fixture.componentInstance['closedCases']().length).toBe(1);
    httpMock.verify();
  });

  it('shows a requirement-update flag on a case that has one', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush([summary({ hasRequirementUpdates: true })]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Requirements have changed');
    httpMock.verify();
  });
});
