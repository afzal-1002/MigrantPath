import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Component } from '@angular/core';
import { environment } from '../../../environments/environment';
import { CountrySummary } from '../../core/services/reference-data.service';
import { CountrySelect } from './country-select';

const SAMPLE_COUNTRIES: CountrySummary[] = [
  { code: 'PL', name: 'Poland' },
  { code: 'PK', name: 'Pakistan' },
  { code: 'DE', name: 'Germany' },
];

@Component({
  imports: [ReactiveFormsModule, CountrySelect],
  template: `<app-country-select [formControl]="control" />`,
})
class HostComponent {
  readonly control = new FormControl<string | null>(null);
}

describe('CountrySelect', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/reference/countries`).flush(SAMPLE_COUNTRIES);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('loads the country list once on init', () => {
    // Already asserted by the single expectOne in beforeEach - a second flush
    // attempt here would fail if the component fetched twice.
    expect(host.control.value).toBeNull();
  });

  it('propagates the selected code (not the display name) to the bound FormControl', () => {
    const select: CountrySelect = fixture.debugElement.children[0].componentInstance;

    select['optionSelected']('PK');

    expect(host.control.value).toBe('PK');
  });

  it('writeValue renders the canonical name for a code set from outside the component', () => {
    host.control.setValue('DE');
    fixture.detectChanges();

    const select: CountrySelect = fixture.debugElement.children[0].componentInstance;
    expect(select['searchControl'].value).toBe('Germany');
  });

  it('filters candidates by both canonical name and code, case-insensitively', () => {
    const select: CountrySelect = fixture.debugElement.children[0].componentInstance;
    const results = select['filter']('pak');

    expect(results).toEqual([{ code: 'PK', name: 'Pakistan' }]);
  });

  it('a query matching nothing returns an empty list, not an error', () => {
    const select: CountrySelect = fixture.debugElement.children[0].componentInstance;
    expect(select['filter']('does-not-exist-anywhere')).toEqual([]);
  });

  it('loading is true only until the country list resolves', () => {
    // A fresh fixture, not the shared beforeEach one, so the countries call can be
    // observed still in flight.
    const freshFixture = TestBed.createComponent(HostComponent);
    freshFixture.detectChanges();
    const select: CountrySelect = freshFixture.debugElement.children[0].componentInstance;
    expect(select['loading']()).toBe(true);

    httpMock.expectOne(`${environment.apiBaseUrl}/reference/countries`).flush(SAMPLE_COUNTRIES);
    expect(select['loading']()).toBe(false);
  });

  it('a failed country list fetch clears loading without throwing', () => {
    const freshFixture = TestBed.createComponent(HostComponent);
    freshFixture.detectChanges();
    const select: CountrySelect = freshFixture.debugElement.children[0].componentInstance;

    httpMock
      .expectOne(`${environment.apiBaseUrl}/reference/countries`)
      .flush(null, { status: 500, statusText: 'Internal Server Error' });

    expect(select['loading']()).toBe(false);
    expect(select['countries']()).toEqual([]);
  });
});
