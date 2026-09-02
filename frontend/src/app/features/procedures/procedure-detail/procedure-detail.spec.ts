import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ProcedureDetail } from '../../../core/services/procedure.service';
import { ProcedureDetailPage } from './procedure-detail';

const SAMPLE_DETAIL: ProcedureDetail = {
  code: 'PESEL',
  name: 'PESEL number assignment',
  summary: 'Get a Polish national ID number',
  description: 'Full description here.',
  category: 'IDENTITY_REGISTRATION',
  jurisdictionScope: 'MUNICIPAL',
  versionNumber: 1,
  effectiveFrom: '2026-01-01',
  steps: [
    {
      code: 'BOOK_APPOINTMENT',
      title: 'Book an appointment',
      description: 'At your district office',
      detailedInstructions: null,
      stepType: 'APPOINTMENT',
      sortOrder: 1,
      mandatory: true,
      onlineAvailable: null,
      requiresAppointment: true,
      expectedUserAction: null,
    },
  ],
  documents: [
    {
      code: 'PASSPORT',
      documentType: 'PASSPORT',
      name: 'Valid passport',
      description: null,
      requirementType: 'DEFAULT_REQUIRED',
      requiredByDefault: true,
      numberOfCopies: null,
      originalRequired: true,
      translationRequired: null,
      swornTranslationRequired: null,
      apostilleRequired: null,
      legalisationRequired: null,
      validityPeriodDescription: null,
      notes: null,
    },
  ],
  fees: [],
  authorities: [],
  offices: [],
  sources: [{ authority: 'City of Warsaw', title: 'Official page', url: 'https://example.gov.pl', role: 'PRIMARY', lastVerifiedAt: null }],
  contentReviewedAt: null,
};

function setUp(code: string | null) {
  return TestBed.configureTestingModule({
    imports: [ProcedureDetailPage],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(code ? { code } : {}) } },
      },
    ],
  }).compileComponents();
}

describe('ProcedureDetailPage', () => {
  let fixture: ComponentFixture<ProcedureDetailPage>;
  let component: ProcedureDetailPage;
  let httpMock: HttpTestingController;

  it('renders the full detail once loaded, including conditional-label logic', async () => {
    await setUp('PESEL');
    fixture = TestBed.createComponent(ProcedureDetailPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/procedures/PESEL`).flush(SAMPLE_DETAIL);
    fixture.detectChanges();

    expect(component['procedure']()).toEqual(SAMPLE_DETAIL);
    expect(fixture.nativeElement.textContent).toContain('PESEL number assignment');
    expect(fixture.nativeElement.textContent).toContain('Book an appointment');
    expect(fixture.nativeElement.textContent).toContain('Valid passport');
    expect(fixture.nativeElement.textContent).toContain('Official page');
    httpMock.verify();
  });

  it('requirementTypeLabel never implies the application evaluated the user', () => {
    expect(component['requirementTypeLabel']('CONDITIONAL')).toBe('May be required depending on your situation');
    expect(component['requirementTypeLabel']('DEFAULT_REQUIRED')).toBe('Required');
  });

  it('shows a not-found state for an unknown code (404)', async () => {
    await setUp('NOPE');
    fixture = TestBed.createComponent(ProcedureDetailPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/procedures/NOPE`).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(component['notFound']()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('could not be found');
    httpMock.verify();
  });

  it('shows a not-found state when no code param is present at all', async () => {
    await setUp(null);
    fixture = TestBed.createComponent(ProcedureDetailPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    expect(component['notFound']()).toBe(true);
    httpMock.verify();
  });
});
