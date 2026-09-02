package com.foreignerwarsaw.reference.country;

import com.foreignerwarsaw.reference.country.dto.CountryDetailResponse;
import com.foreignerwarsaw.reference.country.dto.CountryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only (brief §32) - populating a registration/onboarding country dropdown must never
 * require a session. No write endpoints exist; reference content is Flyway-seeded until Phase 9's
 * admin panel.
 */
@RestController
@RequestMapping("/api/v1/reference/countries")
@Tag(name = "Reference - Countries")
public class CountryController {

  private final CountryService countryService;
  private final CountryClassificationService classificationService;
  private final Clock clock;

  public CountryController(
      CountryService countryService,
      CountryClassificationService classificationService,
      Clock clock) {
    this.countryService = countryService;
    this.classificationService = classificationService;
    this.clock = clock;
  }

  @Operation(summary = "All active countries (code + canonical name), sorted deterministically")
  @GetMapping
  public List<CountryResponse> list() {
    return countryService.listActive().stream().map(CountryResponse::from).toList();
  }

  @Operation(summary = "A single country plus the reference groups it currently belongs to")
  @GetMapping("/{code}")
  public CountryDetailResponse get(@PathVariable String code) {
    Country country = countryService.getByCode(code);
    LocalDate today = LocalDate.now(clock);
    return CountryDetailResponse.of(country, classificationService.classificationsFor(code, today));
  }
}
