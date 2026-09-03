import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { AdminProcedureSummary } from '../../../../core/services/admin/admin-procedure.service';
import { ProcedureAdminList } from './procedure-admin-list';

const BASE = `${environment.apiBaseUrl}/admin/procedures`;

function summary(overrides: Partial<AdminProcedureSummary> = {}): AdminProcedureSummary {
  return {
    code: 'TEST_PROCEDURE',
    categoryCode: 'OTHER',
    canonicalName: 'Test Procedure',
    jurisdictionScope: 'NATIONAL',
    active: true,
    activeVersionNumber: null,
    activeVersionEffectiveFrom: null,
    latestVersionNumber: 1,
    latestVersionStatus: 'DRAFT',
    ...overrides,
  };
}

async function setUp() {
  await TestBed.configureTestingModule({
    imports: [ProcedureAdminList],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  }).compileComponents();
  const fixture = TestBed.createComponent(ProcedureAdminList);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, httpMock };
}

describe('ProcedureAdminList', () => {
  it('lists procedures once loaded', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush([summary()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('TEST_PROCEDURE');
    expect(fixture.nativeElement.textContent).toContain('Test Procedure');
    httpMock.verify();
  });

  it('filters by search text', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush([summary(), summary({ code: 'OTHER_ONE', canonicalName: 'Other Procedure' })]);
    fixture.detectChanges();

    fixture.componentInstance['search'].set('other');
    fixture.detectChanges();

    const filtered = fixture.componentInstance['filtered']();
    expect(filtered.length).toBe(1);
    expect(filtered[0].code).toBe('OTHER_ONE');
    httpMock.verify();
  });

  it('shows the empty state when there are no procedures', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No procedures yet');
    httpMock.verify();
  });
});
