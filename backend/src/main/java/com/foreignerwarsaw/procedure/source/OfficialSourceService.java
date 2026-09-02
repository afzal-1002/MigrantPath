package com.foreignerwarsaw.procedure.source;

import com.foreignerwarsaw.common.web.ApiException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creation and lookup of {@link OfficialSource} rows, plus structural URL validation (brief §61) -
 * deliberately never fetches the URL during save (reachability checks are a separate, not-yet-built
 * concern, brief §61). {@link SourceVerificationService} owns recording verification outcomes.
 */
@Service
public class OfficialSourceService {

  private static final int MAX_URL_LENGTH = 500;

  private final OfficialSourceRepository officialSourceRepository;
  private final Clock clock;

  public OfficialSourceService(OfficialSourceRepository officialSourceRepository, Clock clock) {
    this.officialSourceRepository = officialSourceRepository;
    this.clock = clock;
  }

  @Transactional
  public OfficialSource create(String title, String sourceUrl, SourceType sourceType) {
    validateUrl(sourceUrl);
    OfficialSource source = OfficialSource.draft(title, sourceUrl, sourceType);
    return officialSourceRepository.save(source);
  }

  @Transactional(readOnly = true)
  public OfficialSource getById(UUID id) {
    return officialSourceRepository
        .findById(id)
        .orElseThrow(() -> new OfficialSourceNotFoundException(id));
  }

  /**
   * Structural only (brief §61): a valid http(s) URL, no javascript:/data: schemes, within a sane
   * length. Never confirms the page actually exists.
   */
  private void validateUrl(String sourceUrl) {
    if (sourceUrl == null || sourceUrl.length() > MAX_URL_LENGTH) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URL", "Source URL is missing or too long");
    }
    try {
      URI uri = new URI(sourceUrl);
      String scheme = uri.getScheme();
      if (scheme == null
          || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URL", "Source URL must use http or https");
      }
    } catch (URISyntaxException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URL", "Source URL is not a valid URI");
    }
  }

  Instant now() {
    return clock.instant();
  }
}
