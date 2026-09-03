import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AdminDashboardSummary } from '../../../core/services/admin/admin-dashboard.service';
import { AdminDashboard } from './admin-dashboard';

const BASE = `${environment.apiBaseUrl}/admin/dashboard`;

function summary(overrides: Partial<AdminDashboardSummary> = {}): AdminDashboardSummary {
  return {
    draftProcedureVersions: 0,
    draftRuleVersions: 0,
    draftThresholdVersions: 0,
    draftQuestionnaireVersions: 0,
    pendingReviews: 0,
    approvedProcedureVersionsAwaitingPublication: 0,
    approvedRuleVersionsAwaitingPublication: 0,
    approvedThresholdVersionsAwaitingPublication: 0,
    approvedQuestionnaireVersionsAwaitingPublication: 0,
    sourcesNeedingReview: 0,
    outdatedSources: 0,
    ...overrides,
  };
}

async function setUp() {
  await TestBed.configureTestingModule({
    imports: [AdminDashboard],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  }).compileComponents();
  const fixture = TestBed.createComponent(AdminDashboard);
  const httpMock = TestBed.inject(HttpTestingController);
  return { fixture, httpMock };
}

describe('AdminDashboard', () => {
  it('shows operational counts once loaded', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush(summary({ pendingReviews: 3, outdatedSources: 2 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('3 item(s) pending review');
    expect(fixture.nativeElement.textContent).toContain('2 marked outdated');
    httpMock.verify();
  });

  it('shows an error message when the summary fails to load', async () => {
    const { fixture, httpMock } = await setUp();
    fixture.detectChanges();

    httpMock.expectOne(BASE).flush('error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Could not load the dashboard summary');
    httpMock.verify();
  });
});
