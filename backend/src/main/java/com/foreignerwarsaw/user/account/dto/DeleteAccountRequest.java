package com.foreignerwarsaw.user.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code confirmation} must be the literal string {@code "DELETE"} (brief §4/§29 - an explicit,
 * typed confirmation, not just a checkbox) - checked in {@code AccountController}, not the service
 * layer, since it's a UI-confirmation guard, not a security control (the real security control is
 * the password check).
 */
public record DeleteAccountRequest(
    @NotBlank String currentPassword, @NotBlank String confirmation) {}
