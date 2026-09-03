package com.foreignerwarsaw.admin.dto;

/** Shared by every content type's approve/request-changes/reject admin action (brief §67). */
public record ReviewDecisionRequest(String comment) {}
