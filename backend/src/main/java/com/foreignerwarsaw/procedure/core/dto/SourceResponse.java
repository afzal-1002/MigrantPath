package com.foreignerwarsaw.procedure.core.dto;

import com.foreignerwarsaw.procedure.source.OfficialSource;
import java.time.Instant;

/**
 * Brief §41's exact public shape - deliberately never exposes {@code contentHash} or any other
 * internal field (brief §41: "do not expose internal content hashes").
 */
public record SourceResponse(
    String authority, String title, String url, String role, Instant lastVerifiedAt) {

  public static SourceResponse from(OfficialSource source, String role) {
    return new SourceResponse(
        source.getAuthority() != null ? source.getAuthority().getCanonicalName() : null,
        source.getTitle(),
        source.getSourceUrl(),
        role,
        source.getLastVerifiedAt());
  }
}
