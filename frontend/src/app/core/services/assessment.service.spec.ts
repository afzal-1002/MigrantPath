import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AssessmentDetail, AssessmentService, AssessmentSummary } from './assessment.service';

describe('AssessmentService', () => {
  let service: AssessmentService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/assessments`;

  const sampleDetail: AssessmentDetail = {
    id: 'a1',
    status: 'IN_PROGRESS',
    questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
    questionnaireVersionId: 'v1',
    startedAt: '2026-01-01T00:00:00Z',
    completedAt: null,
    progressPercent: 0,
    sections: [],
    questions: [],
    missingRequiredQuestions: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AssessmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('start posts to /assessments with an empty body', () => {
    service.start().subscribe((result) => expect(result).toEqual(sampleDetail));
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(sampleDetail);
  });

  it('list calls GET /assessments', () => {
    const sample: AssessmentSummary[] = [
      { id: 'a1', status: 'COMPLETED', questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT', startedAt: '2026-01-01T00:00:00Z', completedAt: '2026-01-02T00:00:00Z' },
    ];
    service.list().subscribe((result) => expect(result).toEqual(sample));
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    req.flush(sample);
  });

  it('get fetches one assessment by id', () => {
    service.get('a1').subscribe((result) => expect(result).toEqual(sampleDetail));
    httpMock.expectOne(`${base}/a1`).flush(sampleDetail);
  });

  it('saveAnswer PUTs to the questionCode-scoped answers endpoint', () => {
    service.saveAnswer('a1', 'CURRENTLY_IN_POLAND', { booleanValue: true }).subscribe();
    const req = httpMock.expectOne(`${base}/a1/answers/CURRENTLY_IN_POLAND`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ booleanValue: true });
    req.flush(sampleDetail);
  });

  it('complete posts to the complete endpoint', () => {
    service.complete('a1').subscribe();
    const req = httpMock.expectOne(`${base}/a1/complete`);
    expect(req.request.method).toBe('POST');
    req.flush(sampleDetail);
  });

  it('restart posts to the restart endpoint', () => {
    service.restart('a1').subscribe();
    const req = httpMock.expectOne(`${base}/a1/restart`);
    expect(req.request.method).toBe('POST');
    req.flush(sampleDetail);
  });
});
