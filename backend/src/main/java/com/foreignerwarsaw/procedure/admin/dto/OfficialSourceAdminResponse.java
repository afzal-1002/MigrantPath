package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.source.OfficialSource;
import java.util.UUID;

public record OfficialSourceAdminResponse(
    UUID id, String title, String sourceUrl, String verificationStatus) {

  public static OfficialSourceAdminResponse from(OfficialSource source) {
    return new OfficialSourceAdminResponse(
        source.getId(),
        source.getTitle(),
        source.getSourceUrl(),
        source.getVerificationStatus().name());
  }
}
