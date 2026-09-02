import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthoritySummary, CountryDetail, CountrySummary, OfficeSummary, ReferenceDataService } from './reference-data.service';

describe('ReferenceDataService', () => {
  let service: ReferenceDataService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/reference`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReferenceDataService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listCountries calls GET /reference/countries', () => {
    const sample: CountrySummary[] = [{ code: 'PL', name: 'Poland' }];
    service.listCountries().subscribe((result) => expect(result).toEqual(sample));
    httpMock.expectOne(`${base}/countries`).flush(sample);
  });

  it('getCountry URL-encodes the code and returns its groups', () => {
    const sample: CountryDetail = {
      code: 'GB',
      name: 'United Kingdom',
      groups: [],
      codeStandard: 'ISO_3166_1',
      officiallyAssigned: true,
    };
    service.getCountry('GB').subscribe((result) => expect(result).toEqual(sample));
    httpMock.expectOne(`${base}/countries/GB`).flush(sample);
  });

  it('regionsForCountry calls GET /reference/countries/{code}/regions', () => {
    service.regionsForCountry('PL').subscribe();
    httpMock.expectOne(`${base}/countries/PL/regions`).flush([]);
  });

  it('citiesForRegion calls GET /reference/regions/{code}/cities', () => {
    service.citiesForRegion('MAZOWIECKIE').subscribe();
    httpMock.expectOne(`${base}/regions/MAZOWIECKIE/cities`).flush([]);
  });

  it('districtsForCity calls GET /reference/cities/{code}/districts', () => {
    service.districtsForCity('WARSAW').subscribe();
    httpMock.expectOne(`${base}/cities/WARSAW/districts`).flush([]);
  });

  it('searchAuthorities omits filters that are not provided', () => {
    const sample: AuthoritySummary[] = [];
    service.searchAuthorities({ city: 'WARSAW' }).subscribe((result) => expect(result).toEqual(sample));

    const req = httpMock.expectOne((r) => r.url === `${base}/authorities`);
    expect(req.request.params.get('city')).toBe('WARSAW');
    expect(req.request.params.has('jurisdiction')).toBe(false);
    expect(req.request.params.has('authorityType')).toBe(false);
    req.flush(sample);
  });

  it('searchOffices with no filters sends no query params at all', () => {
    const sample: OfficeSummary[] = [];
    service.searchOffices().subscribe((result) => expect(result).toEqual(sample));

    const req = httpMock.expectOne((r) => r.url === `${base}/offices`);
    expect(req.request.params.keys().length).toBe(0);
    req.flush(sample);
  });
});
