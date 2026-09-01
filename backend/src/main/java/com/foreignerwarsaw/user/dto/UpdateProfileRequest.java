package com.foreignerwarsaw.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Phase 2 profile scope is deliberately minimal (brief §30) - first name and preferred language
 * only. Citizenship, residence status, passport, salary, and family information belong to the
 * immigration profile built in a later phase.
 */
public record UpdateProfileRequest(
    @Size(max = 100) String firstName, @Size(max = 10) String preferredLanguage) {}
