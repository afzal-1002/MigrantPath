import { AsyncPipe } from '@angular/common';
import { Component, forwardRef, inject, input, signal } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable, map, startWith } from 'rxjs';
import { CountrySummary, ReferenceDataService } from '../../core/services/reference-data.service';

/**
 * Reusable, searchable, code-based country picker (Phase 3 brief §36) - the value this
 * component reads/writes via {@link ControlValueAccessor} is always the ISO 3166-1
 * alpha-2 {@link CountrySummary.code}, never the display name, matching how every
 * other part of the system (rule conditions, URLs, foreign keys) identifies a country
 * (see {@code Country.java}'s own Javadoc). Built on {@code MatAutocomplete}
 * specifically for its built-in combobox ARIA semantics (accessible by construction,
 * not by extra work here).
 */
@Component({
  selector: 'app-country-select',
  imports: [AsyncPipe, ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  templateUrl: './country-select.html',
  styleUrl: './country-select.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CountrySelect),
      multi: true,
    },
  ],
})
export class CountrySelect implements ControlValueAccessor {
  private readonly referenceDataService = inject(ReferenceDataService);

  readonly label = input('Country');

  protected readonly searchControl = new FormControl('');
  protected readonly countries = signal<CountrySummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly disabled = signal(false);
  protected filteredCountries$: Observable<CountrySummary[]>;

  // Default no-ops until Angular's forms module calls registerOnChange/registerOnTouched
  // below - standard ControlValueAccessor boilerplate.
  // eslint-disable-next-line @typescript-eslint/no-empty-function
  private onChange: (code: string | null) => void = () => {};
  // eslint-disable-next-line @typescript-eslint/no-empty-function
  private onTouched: () => void = () => {};

  constructor() {
    this.referenceDataService.listCountries().subscribe({
      next: (countries) => {
        this.countries.set(countries);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    this.filteredCountries$ = this.searchControl.valueChanges.pipe(
      startWith(''),
      map((value) => this.filter(value ?? '')),
    );
  }

  /** Shown in the input once a country is selected - the canonical name, even though
   * the underlying form value stays the code. */
  protected displayCountry = (code: string | null): string => {
    if (!code) {
      return '';
    }
    return this.countries().find((c) => c.code === code)?.name ?? code;
  };

  protected optionSelected(code: string): void {
    this.onChange(code);
    this.onTouched();
  }

  private filter(rawValue: string): CountrySummary[] {
    // The autocomplete's own value can be either the raw search text or (once
    // displayWith kicks in) a canonical name - a bare code never appears here, so
    // matching against both name and code is what makes "code-based" still
    // searchable by name too.
    const query = rawValue.toLowerCase();
    return this.countries().filter(
      (c) => c.name.toLowerCase().includes(query) || c.code.toLowerCase().includes(query),
    );
  }

  // --- ControlValueAccessor ---

  writeValue(code: string | null): void {
    this.searchControl.setValue(this.displayCountry(code), { emitEvent: false });
  }

  registerOnChange(fn: (code: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.searchControl.disable({ emitEvent: false });
    } else {
      this.searchControl.enable({ emitEvent: false });
    }
  }
}
