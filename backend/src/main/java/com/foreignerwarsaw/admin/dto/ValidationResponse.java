package com.foreignerwarsaw.admin.dto;

import com.foreignerwarsaw.admin.validation.ValidationIssue;
import java.util.List;

/** Shared by every content type's {@code /validate} admin action (brief §42/§91). */
public record ValidationResponse(boolean valid, List<ValidationIssue> issues) {

  public static ValidationResponse from(List<ValidationIssue> issues) {
    return new ValidationResponse(issues.isEmpty(), issues);
  }
}
