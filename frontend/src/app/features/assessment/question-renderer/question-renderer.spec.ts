import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { QuestionDefinition } from '../../../core/services/assessment.service';
import { QuestionRenderer } from './question-renderer';

function baseQuestion(overrides: Partial<QuestionDefinition>): QuestionDefinition {
  return {
    questionnaireQuestionId: 'qq1',
    questionCode: 'TEST_QUESTION',
    fieldKey: 'testQuestion',
    sectionCode: 'ABOUT_YOU',
    label: 'Test question',
    helpText: null,
    required: false,
    sortOrder: 10,
    answerType: 'TEXT',
    optionSource: 'STATIC',
    allowUnsure: false,
    options: [],
    answer: null,
    ...overrides,
  };
}

function groupFor(value: unknown, unsure = false): FormGroup {
  return new FormGroup({
    value: new FormControl(value),
    unsure: new FormControl(unsure),
  });
}

describe('QuestionRenderer', () => {
  let fixture: ComponentFixture<QuestionRenderer>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuestionRenderer],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(QuestionRenderer);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('BOOLEAN renders a Yes/No radio group', () => {
    fixture.componentRef.setInput('question', baseQuestion({ answerType: 'BOOLEAN' }));
    fixture.componentRef.setInput('group', groupFor(null));
    fixture.detectChanges();

    const radios = fixture.nativeElement.querySelectorAll('mat-radio-button');
    expect(radios.length).toBe(2);
  });

  it('SINGLE_SELECT renders the given options in a select', () => {
    fixture.componentRef.setInput(
      'question',
      baseQuestion({
        answerType: 'SINGLE_SELECT',
        options: [
          { code: 'MARRIED', label: 'Married', description: null },
          { code: 'SINGLE', label: 'Single', description: null },
        ],
      }),
    );
    fixture.componentRef.setInput('group', groupFor(null));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-select')).toBeTruthy();
  });

  it('MULTI_SELECT renders one checkbox per option and toggling updates the array value', () => {
    const group = groupFor([]);
    fixture.componentRef.setInput(
      'question',
      baseQuestion({
        answerType: 'MULTI_SELECT',
        options: [
          { code: 'WORK', label: 'Work', description: null },
          { code: 'STUDY', label: 'Study', description: null },
        ],
      }),
    );
    fixture.componentRef.setInput('group', group);
    fixture.detectChanges();

    const instance = fixture.componentInstance;
    (instance as unknown as { toggleOption(code: string, checked: boolean): void }).toggleOption('WORK', true);
    expect(group.controls['value'].value).toEqual(['WORK']);

    (instance as unknown as { toggleOption(code: string, checked: boolean): void }).toggleOption('WORK', false);
    expect(group.controls['value'].value).toEqual([]);
  });

  it('COUNTRY reuses the shared CountrySelect component', () => {
    fixture.componentRef.setInput('question', baseQuestion({ answerType: 'COUNTRY' }));
    fixture.componentRef.setInput('group', groupFor(null));
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url.endsWith('/reference/countries')).flush([]);

    expect(fixture.nativeElement.querySelector('app-country-select')).toBeTruthy();
  });

  it('checking "not sure" hides the main control', () => {
    fixture.componentRef.setInput('question', baseQuestion({ answerType: 'TEXT', allowUnsure: true }));
    const group = groupFor('', false);
    fixture.componentRef.setInput('group', group);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[matinput]')).toBeTruthy();

    group.controls['unsure'].setValue(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input[matinput]')).toBeFalsy();
    expect(fixture.nativeElement.textContent).toContain("I'm not sure");
  });
});
