package com.foreignerwarsaw.admin.validation;

/**
 * One structured publish-readiness problem (brief §42/§91) - {@code code} is a stable machine
 * identifier the frontend can key off (e.g. to highlight a specific field), {@code message} is the
 * human-readable explanation shown in the Validation panel.
 */
public record ValidationIssue(String code, String message) {}
