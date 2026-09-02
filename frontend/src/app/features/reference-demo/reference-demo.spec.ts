import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ReferenceDemo } from './reference-demo';

describe('ReferenceDemo', () => {
  let fixture: ComponentFixture<ReferenceDemo>;
  let component: ReferenceDemo;
  let httpMock: HttpTestingController;
  const base = `${environment.apiBaseUrl}/reference`;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReferenceDemo],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(ReferenceDemo);
    component = fixture.componentInstance;
    fixture.detectChanges();
    httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(`${base}/countries`).flush([{ code: 'PK', name: 'Pakistan' }]);
  });

  afterEach(() => httpMock.verify());

  it('starts with region/city/district all disabled', () => {
    expect(component['geographyForm'].controls.region.disabled).toBe(true);
    expect(component['geographyForm'].controls.city.disabled).toBe(true);
    expect(component['geographyForm'].controls.district.disabled).toBe(true);
  });

  it('selecting Pakistan then Poland -> Mazowieckie -> Warsaw resolves the real district list', () => {
    // Same scenario the brief names explicitly - selecting a non-EU nationality first
    // (Pakistan, exercising the third-country path) has no bearing on the geography
    // cascade that follows for Poland, which is the point: geography and nationality
    // classification are entirely independent concerns here.
    component['countryControl'].setValue('PK');
    httpMock.expectOne(`${base}/countries/PK/regions`).flush([]);
    component['countryControl'].setValue('PL');
    httpMock
      .expectOne(`${base}/countries/PL/regions`)
      .flush([{ code: 'MAZOWIECKIE', name: 'Mazowieckie', regionType: 'VOIVODESHIP' }]);
    expect(component['geographyForm'].controls.region.enabled).toBe(true);

    component['geographyForm'].controls.region.setValue('MAZOWIECKIE');
    httpMock.expectOne(`${base}/regions/MAZOWIECKIE/cities`).flush([{ code: 'WARSAW', name: 'Warszawa' }]);
    expect(component['geographyForm'].controls.city.enabled).toBe(true);

    component['geographyForm'].controls.city.setValue('WARSAW');
    httpMock.expectOne(`${base}/cities/WARSAW/districts`).flush([
      { code: 'SRODMIESCIE', name: 'Śródmieście' },
      { code: 'MOKOTOW', name: 'Mokotów' },
    ]);

    expect(component['geographyForm'].controls.district.enabled).toBe(true);
    expect(component['districts']()).toEqual([
      { code: 'SRODMIESCIE', name: 'Śródmieście' },
      { code: 'MOKOTOW', name: 'Mokotów' },
    ]);
  });

  it('clearing the region resets and disables city and district downstream', () => {
    component['countryControl'].setValue('PL');
    httpMock
      .expectOne(`${base}/countries/PL/regions`)
      .flush([{ code: 'MAZOWIECKIE', name: 'Mazowieckie', regionType: 'VOIVODESHIP' }]);
    component['geographyForm'].controls.region.setValue('MAZOWIECKIE');
    httpMock.expectOne(`${base}/regions/MAZOWIECKIE/cities`).flush([{ code: 'WARSAW', name: 'Warszawa' }]);
    component['geographyForm'].controls.city.setValue('WARSAW');
    httpMock.expectOne(`${base}/cities/WARSAW/districts`).flush([{ code: 'WOLA', name: 'Wola' }]);

    component['geographyForm'].controls.region.setValue(null);

    expect(component['geographyForm'].controls.city.disabled).toBe(true);
    expect(component['geographyForm'].controls.district.disabled).toBe(true);
    expect(component['cities']()).toEqual([]);
    expect(component['districts']()).toEqual([]);
  });
});
