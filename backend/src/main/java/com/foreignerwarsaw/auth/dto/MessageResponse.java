package com.foreignerwarsaw.auth.dto;

/**
 * Generic body for endpoints that intentionally return the same response regardless of outcome
 * (forgot-password, resend-verification) to avoid account enumeration (brief §9/§14/§46).
 */
public record MessageResponse(String message) {}
