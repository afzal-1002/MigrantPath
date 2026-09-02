import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AssessmentDetail, QuestionDefinition } from '../../../core/services/assessment.service';
import { AssessmentWizard } from './assessment-wizard';

const BASE = `${environment.apiBaseUrl}/assessments`;

function q(overrides: Partial<QuestionDefinition>): QuestionDefinition {
  return {
    questionnaireQuestionId: overrides.questionCode ?? 'qq',
    questionCode: 'Q',
    fieldKey: 'q',
    sectionCode: 'ABOUT_YOU',
    label: 'Question',
    helpText: null,
    required: false,
    sortOrder: 10,
    answerType: 'BOOLEAN',
    optionSource: 'STATIC',
    allowUnsure: false,
    options: [],
    answer: null,
    ...overrides,
  };
}

const CURRENTLY_IN_POLAND = q({
  questionCode: 'CURRENTLY_IN_POLAND',
  label: 'Are you currently in Poland?',
  required: true,
  sectionCode: 'ABOUT_YOU',
  sortOrder: 10,
});

const HAS_JOB_OFFER = q({
  questionCode: 'HAS_JOB_OFFER',
  label: 'Do you have a job offer?',
  sectionCode: 'WORK',
  sortOrder: 20,
});

function detail(overrides: Partial<AssessmentDetail>): AssessmentDetail {
  return {
    id: 'a1',
    status: 'IN_PROGRESS',
    questionnaireCode: 'WARSAW_GENERAL_ASSESSMENT',
    questionnaireVersionId: 'v1',
    startedAt: '2026-01-01T00:00:00Z',
    completedAt: null,
    progressPercent: 0,
    sections: [
      { code: 'ABOUT_YOU', title: 'About you', sortOrder: 10 },
      { code: 'WORK', title: 'Work', sortOrder: 20 },
    ],
    questions: [CURRENTLY_IN_POLAND, HAS_JOB_OFFER],
    missingRequiredQuestions: [{ questionCode: 'CURRENTLY_IN_POLAND', label: 'Are you currently in Poland?', sectionCode: 'ABOUT_YOU' }],
    ...overrides,
  };
}

async function setUp(): Promise<{ fixture: ComponentFixture<AssessmentWizard>; httpMock: HttpTestingController; router: Router }> {
  await TestBed.configureTestingModule({
    imports: [AssessmentWizard],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'a1' }) } } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(AssessmentWizard);
  const httpMock = TestBed.inject(HttpTestingController);
  const router = TestBed.inject(Router);
  fixture.detectChanges();
  httpMock.expectOne(`${BASE}/a1`).flush(detail({}));
  fixture.detectChanges();
  return { fixture, httpMock, router };
}

describe('AssessmentWizard', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('loads the assessment and renders the first section', async () => {
    const { fixture } = await setUp();
    expect(fixture.nativeElement.textContent).toContain('Step 1 of 2');
    expect(fixture.nativeElement.textContent).toContain('Are you currently in Poland?');
  });

  it('Next saves the answered questions and advances to the next section', async () => {
    const { fixture, httpMock } = await setUp();
    const component = fixture.componentInstance;

    // BOOLEAN autosaves immediately on change (see IMMEDIATE_SAVE_TYPES).
    component['formGroup']().controls['CURRENTLY_IN_POLAND'].controls['value'].setValue(true);
    httpMock
      .expectOne(`${BASE}/a1/answers/CURRENTLY_IN_POLAND`)
      .flush(detail({ questions: [{ ...CURRENTLY_IN_POLAND, answer: { stringValue: null, booleanValue: true, integerValue: null, decimalValue: null, dateValue: null, referenceCode: null, selectedOptionCodes: null, unsure: false } }, HAS_JOB_OFFER] }));
    fixture.detectChanges();

    component['next']();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Step 2 of 2');
    expect(fixture.nativeElement.textContent).toContain('Do you have a job offer?');
  });

  it('a same-section branch reveal appears without leaving the section', async () => {
    const { fixture, httpMock } = await setUp();
    const component = fixture.componentInstance;
    // Force the wizard onto a WORK-only section with just HAS_JOB_OFFER visible so far.
    component['detail'].set(detail({ sections: [{ code: 'WORK', title: 'Work', sortOrder: 20 }], questions: [HAS_JOB_OFFER] }));
    component['currentSectionIndex'].set(0);
    (component as unknown as { buildFormForCurrentSection(): void })['buildFormForCurrentSection']();
    fixture.detectChanges();

    component['formGroup']().controls['HAS_JOB_OFFER'].controls['value'].setValue(true);
    const salary = q({ questionCode: 'MONTHLY_GROSS_SALARY', label: 'Monthly gross salary', answerType: 'DECIMAL', sectionCode: 'WORK', sortOrder: 30 });
    httpMock
      .expectOne(`${BASE}/a1/answers/HAS_JOB_OFFER`)
      .flush(
        detail({
          sections: [{ code: 'WORK', title: 'Work', sortOrder: 20 }],
          questions: [
            { ...HAS_JOB_OFFER, answer: { stringValue: null, booleanValue: true, integerValue: null, decimalValue: null, dateValue: null, referenceCode: null, selectedOptionCodes: null, unsure: false } },
            salary,
          ],
        }),
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Monthly gross salary');
  });

  it('review lists answered questions and Complete posts to the complete endpoint', async () => {
    const { fixture, httpMock } = await setUp();
    const component = fixture.componentInstance;
    component['showReview'].set(true);
    component['detail'].set(
      detail({
        questions: [
          { ...CURRENTLY_IN_POLAND, answer: { stringValue: null, booleanValue: true, integerValue: null, decimalValue: null, dateValue: null, referenceCode: null, selectedOptionCodes: null, unsure: false } },
        ],
        missingRequiredQuestions: [],
      }),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Review your answers');
    expect(fixture.nativeElement.textContent).toContain('Yes');

    component['complete']();
    httpMock.expectOne(`${BASE}/a1/complete`).flush(detail({ status: 'COMPLETED', completedAt: '2026-01-03T00:00:00Z' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Your assessment is complete');
    expect(fixture.nativeElement.textContent).toContain('Pathway analysis will be available in the next implementation phase.');
  });

  it('an incomplete-completion error jumps back to the section with the missing question', async () => {
    const { fixture, httpMock } = await setUp();
    const component = fixture.componentInstance;
    component['showReview'].set(true);
    fixture.detectChanges();

    component['complete']();
    httpMock.expectOne(`${BASE}/a1/complete`).flush(
      { code: 'ASSESSMENT_INCOMPLETE', errors: [{ field: 'CURRENTLY_IN_POLAND', message: 'This question is required.' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(component['showReview']()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Answer every required question');
  });
});
