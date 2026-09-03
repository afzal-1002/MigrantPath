package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.usercase.engine.RequirementChangeReport;
import java.util.List;

public record RequirementChangeReportResponse(
    boolean newerVersionAvailable, List<RequirementChangeResponse> changes) {

  public static RequirementChangeReportResponse from(RequirementChangeReport report) {
    return new RequirementChangeReportResponse(
        report.newerVersionAvailable(),
        report.changes().stream().map(RequirementChangeResponse::from).toList());
  }
}
