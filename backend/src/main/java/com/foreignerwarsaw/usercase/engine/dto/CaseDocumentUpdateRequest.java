package com.foreignerwarsaw.usercase.engine.dto;

/**
 * Either field may be {@code null} (unset, "don't change") - brief §37's own-note feature kept on
 * the same PATCH as status rather than a separate endpoint for one small optional field.
 */
public record CaseDocumentUpdateRequest(String status, String userNote) {}
