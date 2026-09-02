import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Typed mirrors of the Phase 3 backend DTOs (docs/database/DATABASE.md §2) - public,
 * read-only reference lookup only. Never a procedure/eligibility recommendation (brief
 * §31); a future Phase 4+ service consumes these as one input among several, exactly
 * like {@code CountryClassificationService} on the backend.
 */
export interface CountrySummary {
  code: string;
  name: string;
}

export interface CountryDetail {
  code: string;
  name: string;
  groups: string[];
  /** 'ISO_3166_1' for the normal case; 'USER_ASSIGNED' only for `XK` (Kosovo) as of
   * Phase 3 - see docs/database/DATABASE.md §2 and ADR-006. */
  codeStandard: string;
  officiallyAssigned: boolean;
}

export interface RegionSummary {
  code: string;
  name: string;
  regionType: string;
}

export interface CitySummary {
  code: string;
  name: string;
}

export interface DistrictSummary {
  code: string;
  name: string;
}

export interface AuthoritySummary {
  code: string;
  name: string;
  authorityType: string;
  jurisdictionCode: string;
  officialWebsite: string | null;
}

export interface OfficeSummary {
  code: string;
  authorityCode: string;
  name: string;
  street: string | null;
  buildingNumber: string | null;
  postalCode: string | null;
  cityCode: string;
  districtCode: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  appointmentRequired: boolean | null;
  bookingUrl: string | null;
  services: string[];
}

export interface OfficeSearchFilters {
  city?: string;
  district?: string;
  authority?: string;
  service?: string;
}

export interface AuthoritySearchFilters {
  jurisdiction?: string;
  city?: string;
  authorityType?: string;
}

/**
 * Thin, uncached HTTP client for {@code /api/v1/reference/**} (brief §35's "no
 * write endpoints" is mirrored here - this service has no create/update/delete
 * methods at all). The backend already caches the active-countries list
 * ({@code CountryService#listActive}); duplicating that here would be a second cache
 * to keep consistent for no real benefit at this data size.
 */
@Injectable({ providedIn: 'root' })
export class ReferenceDataService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/reference`;

  listCountries(): Observable<CountrySummary[]> {
    return this.http.get<CountrySummary[]>(`${this.base}/countries`);
  }

  getCountry(code: string): Observable<CountryDetail> {
    return this.http.get<CountryDetail>(`${this.base}/countries/${encodeURIComponent(code)}`);
  }

  regionsForCountry(countryCode: string): Observable<RegionSummary[]> {
    return this.http.get<RegionSummary[]>(`${this.base}/countries/${encodeURIComponent(countryCode)}/regions`);
  }

  citiesForRegion(regionCode: string): Observable<CitySummary[]> {
    return this.http.get<CitySummary[]>(`${this.base}/regions/${encodeURIComponent(regionCode)}/cities`);
  }

  districtsForCity(cityCode: string): Observable<DistrictSummary[]> {
    return this.http.get<DistrictSummary[]>(`${this.base}/cities/${encodeURIComponent(cityCode)}/districts`);
  }

  searchAuthorities(filters: AuthoritySearchFilters = {}): Observable<AuthoritySummary[]> {
    return this.http.get<AuthoritySummary[]>(`${this.base}/authorities`, {
      params: this.toHttpParams(filters),
    });
  }

  searchOffices(filters: OfficeSearchFilters = {}): Observable<OfficeSummary[]> {
    return this.http.get<OfficeSummary[]>(`${this.base}/offices`, { params: this.toHttpParams(filters) });
  }

  private toHttpParams(filters: OfficeSearchFilters | AuthoritySearchFilters): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value) {
        params = params.set(key, value as string);
      }
    }
    return params;
  }
}
