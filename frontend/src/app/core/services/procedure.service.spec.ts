import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ProcedureDetail, ProcedureService, ProcedureSummary } from './procedure.service';

describe('ProcedureService', () => {
  let service: ProcedureService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/procedures`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProcedureService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getProcedures calls GET /procedures', () => {
    const sample: ProcedureSummary[] = [
      { code: 'PESEL', name: 'PESEL', category: 'IDENTITY_REGISTRATION', summary: null, jurisdictionScope: 'MUNICIPAL' },
    ];
    service.getProcedures().subscribe((result) => expect(result).toEqual(sample));
    httpMock.expectOne(base).flush(sample);
  });

  it('getProcedure URL-encodes the code and returns the full detail', () => {
    const sample: ProcedureDetail = {
      code: 'PESEL',
      name: 'PESEL',
      summary: null,
      description: null,
      category: 'IDENTITY_REGISTRATION',
      jurisdictionScope: 'MUNICIPAL',
      versionNumber: 1,
      effectiveFrom: '2026-01-01',
      steps: [],
      documents: [],
      fees: [],
      authorities: [],
      offices: [],
      sources: [],
      contentReviewedAt: null,
    };
    service.getProcedure('PESEL').subscribe((result) => expect(result).toEqual(sample));
    httpMock.expectOne(`${base}/PESEL`).flush(sample);
  });
});
