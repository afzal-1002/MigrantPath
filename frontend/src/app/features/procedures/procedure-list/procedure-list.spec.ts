import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ProcedureSummary } from '../../../core/services/procedure.service';
import { ProcedureList } from './procedure-list';

describe('ProcedureList', () => {
  let fixture: ComponentFixture<ProcedureList>;
  let component: ProcedureList;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/procedures`;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProcedureList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ProcedureList);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('starts in the loading state', () => {
    expect(component['loading']()).toBe(true);
    // Flush the in-flight request the constructor already fired, so afterEach's
    // httpMock.verify() doesn't see it as an unexpectedly-open request.
    httpMock.expectOne(base).flush([]);
  });

  it('shows an empty list gracefully when nothing is published yet', () => {
    httpMock.expectOne(base).flush([]);
    fixture.detectChanges();

    expect(component['loading']()).toBe(false);
    expect(component['procedures']()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('No procedures are published yet');
  });

  it('renders each returned procedure as a card', () => {
    const sample: ProcedureSummary[] = [
      { code: 'PESEL', name: 'PESEL', category: 'IDENTITY_REGISTRATION', summary: 'Get a PESEL number', jurisdictionScope: 'MUNICIPAL' },
    ];
    httpMock.expectOne(base).flush(sample);
    fixture.detectChanges();

    expect(component['procedures']()).toEqual(sample);
    expect(fixture.nativeElement.textContent).toContain('PESEL');
    expect(fixture.nativeElement.textContent).toContain('Get a PESEL number');
  });

  it('shows an error state when the request fails', () => {
    httpMock.expectOne(base).flush(null, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component['error']()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Something went wrong');
  });
});
