import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { switchMap, tap } from 'rxjs';
import { CitySummary, DistrictSummary, RegionSummary, ReferenceDataService } from '../../core/services/reference-data.service';
import { CountrySelect } from '../../shared/country-select/country-select';

/**
 * Phase 3 verification page (brief §37) - proves the reference-data API end to end
 * through real Angular components and real HTTP calls, not hardcoded fixtures. Not a
 * product feature: a future procedure/questionnaire flow (Phase 4+) will use {@link
 * CountrySelect} and {@link ReferenceDataService} directly, not this page. Kept simple
 * on purpose - this exists to be driven by Playwright, not to look finished.
 */
@Component({
  selector: 'app-reference-demo',
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatSelectModule, CountrySelect],
  templateUrl: './reference-demo.html',
  styleUrl: './reference-demo.scss',
})
export class ReferenceDemo {
  private readonly referenceDataService = inject(ReferenceDataService);

  protected readonly countryControl = new FormControl<string | null>(null);
  protected readonly geographyForm = new FormGroup({
    region: new FormControl<string | null>({ value: null, disabled: true }),
    city: new FormControl<string | null>({ value: null, disabled: true }),
    district: new FormControl<string | null>({ value: null, disabled: true }),
  });

  protected readonly regions = signal<RegionSummary[]>([]);
  protected readonly cities = signal<CitySummary[]>([]);
  protected readonly districts = signal<DistrictSummary[]>([]);

  constructor() {
    this.countryControl.valueChanges.subscribe((countryCode) => {
      this.resetFrom('region');
      if (!countryCode) {
        return;
      }
      this.geographyForm.controls.region.enable();
      this.referenceDataService.regionsForCountry(countryCode).subscribe((regions) => this.regions.set(regions));
    });

    this.geographyForm.controls.region.valueChanges.subscribe((regionCode) => {
      this.resetFrom('city');
      if (!regionCode) {
        return;
      }
      this.geographyForm.controls.city.enable();
      this.referenceDataService.citiesForRegion(regionCode).subscribe((cities) => this.cities.set(cities));
    });

    this.geographyForm.controls.city.valueChanges
      .pipe(
        tap(() => this.resetFrom('district')),
        switchMap((cityCode) => (cityCode ? this.referenceDataService.districtsForCity(cityCode) : [])),
      )
      .subscribe((districts) => {
        // A cleared city (cityCode falsy above) never reaches here - switchMap's `[]`
        // completes without emitting, so resetFrom('district')'s own clearing (in the
        // tap above) is the only thing that runs in that case.
        this.geographyForm.controls.district.enable();
        this.districts.set(districts);
      });
  }

  private resetFrom(level: 'region' | 'city' | 'district'): void {
    if (level === 'region') {
      this.regions.set([]);
      this.geographyForm.controls.region.reset(null, { emitEvent: false });
      this.geographyForm.controls.region.disable({ emitEvent: false });
    }
    if (level === 'region' || level === 'city') {
      this.cities.set([]);
      this.geographyForm.controls.city.reset(null, { emitEvent: false });
      this.geographyForm.controls.city.disable({ emitEvent: false });
    }
    this.districts.set([]);
    this.geographyForm.controls.district.reset(null, { emitEvent: false });
    this.geographyForm.controls.district.disable({ emitEvent: false });
  }
}
