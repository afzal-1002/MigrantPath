import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { AdminRuleVersionDetail } from '../../../../core/services/admin/admin-rule.service';
import { RuleVersionEditor } from './rule-version-editor';

const FACTS_BASE = `${environment.apiBaseUrl}/admin/facts`;
const VERSIONS_BASE = `${environment.apiBaseUrl}/admin/rules/TEST_RULE`;

function version(overrides: Partial<AdminRuleVersionDetail> = {}): AdminRuleVersionDetail {
  return {
    id: 'v1',
    ruleCode: 'TEST_RULE',
    versionNumber: 1,
    status: 'DRAFT',
    conditionTree: '{"fact":"CITIZENSHIP_COUNTRY","operator":"EXISTS"}',
    explanationKey: null,
    changeSummary: null,
    effectiveFrom: null,
    effectiveTo: null,
    lockVersion: 0,
    createdBy: null,
    submittedBy: null,
    approvedBy: null,
    publishedBy: null,
    submittedAt: null,
    approvedAt: null,
    publishedAt: null,
    sources: [],
    ...overrides,
  };
}

async function setUp() {
  await TestBed.configureTestingModule({
    imports: [RuleVersionEditor],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ code: 'TEST_RULE', versionNumber: '1' }) } },
      },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(RuleVersionEditor);
  const httpMock = TestBed.inject(HttpTestingController);
  httpMock.expectOne(FACTS_BASE).flush([
    { code: 'CITIZENSHIP_COUNTRY', valueType: 'COUNTRY', derived: false, allowedOperators: ['EQUALS', 'EXISTS'] },
    { code: 'AGE_YEARS', valueType: 'INTEGER', derived: true, allowedOperators: ['GREATER_THAN_OR_EQUAL'] },
  ]);
  httpMock.expectOne(VERSIONS_BASE).flush([version()]);
  return { fixture, httpMock };
}

describe('RuleVersionEditor', () => {
  it('parses a single-leaf condition tree into the structured builder', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    expect(fixture.componentInstance['builderMode']()).toBe('simple');
    expect(fixture.componentInstance['leaves']().length).toBe(1);
    expect(fixture.componentInstance['leaves']()[0].fact).toBe('CITIZENSHIP_COUNTRY');
    httpMock.verify();
  });

  it('falls back to Advanced JSON mode for a tree the structured builder cannot represent', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    (
      fixture.componentInstance as unknown as { parseIntoBuilder(json: string): void }
    ).parseIntoBuilder('{"not":{"fact":"CITIZENSHIP_COUNTRY","operator":"EXISTS"}}');

    expect(fixture.componentInstance['builderMode']()).toBe('advanced');
    httpMock.verify();
  });

  it('rebuilds a single-condition JSON tree from the structured builder', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    const built = JSON.parse(fixture.componentInstance['buildTreeText']());
    expect(built.fact).toBe('CITIZENSHIP_COUNTRY');
    expect(built.operator).toBe('EXISTS');
    httpMock.verify();
  });

  it('combines multiple conditions under the selected combinator', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    fixture.componentInstance['addLeaf']();
    fixture.componentInstance['combinator'].set('ANY');
    const built = JSON.parse(fixture.componentInstance['buildTreeText']());

    expect(Array.isArray(built.any)).toBe(true);
    expect(built.any.length).toBe(2);
    httpMock.verify();
  });
});
