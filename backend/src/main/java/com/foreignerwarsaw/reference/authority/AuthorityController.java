package com.foreignerwarsaw.reference.authority;

import com.foreignerwarsaw.reference.authority.dto.AuthorityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only. No procedure/requirement content is associated here - that's Phase 4+ (brief
 * §12/§67).
 */
@RestController
@RequestMapping("/api/v1/reference/authorities")
@Tag(name = "Reference - Authorities")
public class AuthorityController {

  private final AuthorityService authorityService;

  public AuthorityController(AuthorityService authorityService) {
    this.authorityService = authorityService;
  }

  @Operation(
      summary =
          "Government/administrative bodies, optionally filtered by jurisdiction, city, or authority type")
  @GetMapping
  public List<AuthorityResponse> search(
      @RequestParam(required = false) String jurisdiction,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String authorityType) {
    return authorityService.search(jurisdiction, city, authorityType).stream()
        .map(AuthorityResponse::from)
        .toList();
  }
}
