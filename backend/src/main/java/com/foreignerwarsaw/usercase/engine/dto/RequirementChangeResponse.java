package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.usercase.engine.RequirementChange;

public record RequirementChangeResponse(
    String changeType, String category, String stableCode, String title, String detail) {

  public static RequirementChangeResponse from(RequirementChange change) {
    return new RequirementChangeResponse(
        change.changeType(),
        change.category(),
        change.stableCode(),
        change.title(),
        change.detail());
  }
}
