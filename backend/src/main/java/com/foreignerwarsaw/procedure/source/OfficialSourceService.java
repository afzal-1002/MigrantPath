package com.foreignerwarsaw.procedure.source;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.core.ProcedureVersionSourceRepository;
import com.foreignerwarsaw.procedure.threshold.ThresholdVersionSourceRepository;
import com.foreignerwarsaw.reference.authority.Authority;
import com.foreignerwarsaw.reference.geography.Jurisdiction;
import com.foreignerwarsaw.rules.core.RuleVersionSourceRepository;
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
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final RuleVersionSourceRepository ruleVersionSourceRepository;
  private final ThresholdVersionSourceRepository thresholdVersionSourceRepository;
  private final Clock clock;

  public OfficialSourceService(
      OfficialSourceRepository officialSourceRepository,
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      RuleVersionSourceRepository ruleVersionSourceRepository,
      ThresholdVersionSourceRepository thresholdVersionSourceRepository,
      Clock clock) {
    this.officialSourceRepository = officialSourceRepository;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    this.ruleVersionSourceRepository = ruleVersionSourceRepository;
    this.thresholdVersionSourceRepository = thresholdVersionSourceRepository;
    this.clock = clock;
  }

  @Transactional
  public OfficialSource create(String title, String sourceUrl, SourceType sourceType) {
    validateUrl(sourceUrl);
    OfficialSource source = OfficialSource.draft(title, sourceUrl, sourceType);
    return officialSourceRepository.save(source);
  }

  /**
   * Pre-Phase-10 hardening (brief §C): legally significant {@link OfficialSource} identity - in
   * this codebase, {@code title}/{@code sourceUrl}/{@code sourceType} (which already have no setter
   * at all, structurally immutable since Phase 4) plus {@code authority} - must never silently
   * change once real published content's historical provenance depends on it. {@link
   * #assertIdentityEditable} is the one gate any future identity-editing action must call; {@link
   * #updateOperationalMetadata} is the one currently-wired action that uses it, guarding only the
   * {@code authority} field it touches - {@code jurisdiction}/{@code language} are looser
   * operational/descriptive metadata, not gated (brief's own "current operational metadata... may
   * remain live" allowance).
   *
   * <p>Once a source has ever backed a version that reached {@code PUBLISHED} (directly or via a
   * since-{@code ARCHIVED} version - the only path to {@code ARCHIVED} is through {@code
   * PUBLISHED}), its identity is locked forever - the correct fix for a materially different source
   * is a brand-new {@link OfficialSource} row, never an edit that would rewrite what a historical
   * case's provenance chain actually pointed to at the time.
   */
  @Transactional(readOnly = true)
  public void assertIdentityEditable(UUID sourceId) {
    boolean used =
        procedureVersionSourceRepository.existsUsedByPublishedVersion(sourceId)
            || ruleVersionSourceRepository.existsUsedByPublishedVersion(sourceId)
            || thresholdVersionSourceRepository.existsUsedByPublishedVersion(sourceId);
    if (used) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "SOURCE_IDENTITY_LOCKED",
          "This source's identity is locked because it has backed published content - create a"
              + " new source instead of editing this one's authority/title/URL/type");
    }
  }

  /**
   * The one currently-wired admin action that can change {@code authority} (previously unreachable
   * through any endpoint) - guarded by {@link #assertIdentityEditable}. {@code jurisdiction}/{@code
   * language} are always editable (operational metadata).
   */
  @Transactional
  public OfficialSource updateOperationalMetadata(
      UUID sourceId, Authority authority, Jurisdiction jurisdiction, String language) {
    OfficialSource source = getById(sourceId);
    boolean authorityChanged =
        authority != null
            && (source.getAuthority() == null
                || !authority.getId().equals(source.getAuthority().getId()));
    if (authorityChanged) {
      assertIdentityEditable(sourceId);
      source.setAuthority(authority);
    }
    source.setJurisdiction(jurisdiction);
    source.setLanguage(language);
    return source;
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
