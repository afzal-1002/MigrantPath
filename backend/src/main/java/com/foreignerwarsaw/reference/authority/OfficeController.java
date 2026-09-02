package com.foreignerwarsaw.reference.authority;

import com.foreignerwarsaw.reference.authority.dto.OfficeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only reference lookup - never a procedure/office recommendation (brief §31: "This
 * API is reference lookup only"). Phase 3 seeds exactly one verified office
 * (docs/reference/REFERENCE_DATA_SOURCES.md); PESEL/meldunek/driving-licence district routing is
 * deferred to Phase 10.
 */
@RestController
@RequestMapping("/api/v1/reference/offices")
@Tag(name = "Reference - Offices")
public class OfficeController {

  private final OfficeLookupService officeLookupService;

  public OfficeController(OfficeLookupService officeLookupService) {
    this.officeLookupService = officeLookupService;
  }

  @Operation(summary = "Offices, optionally filtered by city, district, authority, or service type")
  @GetMapping
  public List<OfficeResponse> search(
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String district,
      @RequestParam(required = false) String authority,
      @RequestParam(required = false) String service) {
    return officeLookupService.search(city, district, authority, service).stream()
        .map(office -> OfficeResponse.of(office, officeLookupService.serviceCodesFor(office)))
        .toList();
  }
}
