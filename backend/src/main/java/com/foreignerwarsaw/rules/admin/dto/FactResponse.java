package com.foreignerwarsaw.rules.admin.dto;

import com.foreignerwarsaw.rules.evaluation.FactDefinition;
import java.util.List;

public record FactResponse(
    String code, String valueType, boolean derived, List<String> allowedOperators) {

  public static FactResponse from(FactDefinition fact) {
    return new FactResponse(
        fact.code(),
        fact.valueType().name(),
        fact.derived(),
        fact.allowedOperators().stream().map(Enum::name).sorted().toList());
  }
}
