package com.foreignerwarsaw.reference.geography;

import com.foreignerwarsaw.reference.geography.dto.CityResponse;
import com.foreignerwarsaw.reference.geography.dto.DistrictResponse;
import com.foreignerwarsaw.reference.geography.dto.RegionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only, same rationale as {@link
 * com.foreignerwarsaw.reference.country.CountryController}.
 */
@RestController
@RequestMapping("/api/v1/reference")
@Tag(name = "Reference - Geography")
public class GeographyController {

  private final GeographyService geographyService;

  public GeographyController(GeographyService geographyService) {
    this.geographyService = geographyService;
  }

  @Operation(summary = "Active regions (voivodeships, for Poland) belonging to a country")
  @GetMapping("/countries/{countryCode}/regions")
  public List<RegionResponse> regionsForCountry(@PathVariable String countryCode) {
    return geographyService.regionsForCountry(countryCode).stream()
        .map(RegionResponse::from)
        .toList();
  }

  @Operation(summary = "Active cities belonging to a region - only Warsaw is active in V1")
  @GetMapping("/regions/{regionCode}/cities")
  public List<CityResponse> citiesForRegion(@PathVariable String regionCode) {
    return geographyService.citiesForRegion(regionCode).stream().map(CityResponse::from).toList();
  }

  @Operation(
      summary =
          "Active districts belonging to a city - all 18 official Warsaw districts for WARSAW")
  @GetMapping("/cities/{cityCode}/districts")
  public List<DistrictResponse> districtsForCity(@PathVariable String cityCode) {
    return geographyService.districtsForCity(cityCode).stream()
        .map(DistrictResponse::from)
        .toList();
  }
}
